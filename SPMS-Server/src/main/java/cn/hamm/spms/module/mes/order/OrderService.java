package cn.hamm.spms.module.mes.order;

import cn.hamm.airpower.core.CollectionUtil;
import cn.hamm.airpower.core.DictionaryUtil;
import cn.hamm.airpower.core.NumberUtil;
import cn.hamm.airpower.core.interfaces.IDictionary;
import cn.hamm.airpower.curd.helper.ExportHelper;
import cn.hamm.airpower.curd.model.query.Page;
import cn.hamm.airpower.curd.model.query.QueryListRequest;
import cn.hamm.airpower.curd.model.query.QueryPageRequest;
import cn.hamm.airpower.curd.model.query.QueryPageResponse;
import cn.hamm.airpower.curd.model.query.Sort;
import cn.hamm.spms.base.bill.AbstractBaseBillService;
import cn.hamm.spms.base.mapper.ReadStat;
import cn.hamm.spms.common.CsvFormulaSanitizer;
import cn.hamm.spms.module.mes.MesServices;
import cn.hamm.spms.module.mes.order.detail.OrderDetailEntity;
import cn.hamm.spms.module.mes.order.detail.OrderDetailRepository;
import cn.hamm.spms.module.mes.order.detail.OrderDetailService;
import cn.hamm.spms.module.mes.order.enums.OrderStatus;
import cn.hamm.spms.module.mes.order.enums.OrderType;
import cn.hamm.spms.module.mes.order.mapper.OrderMapper;
import cn.hamm.spms.module.mes.order.mapper.OrderReadShadowService;
import cn.hamm.spms.module.asset.material.MaterialService;
import cn.hamm.spms.module.channel.ChannelServices;
import cn.hamm.spms.module.system.SystemServices;
import cn.hamm.spms.module.system.config.ConfigEntity;
import cn.hamm.spms.module.system.config.ConfigService;
import cn.hamm.spms.module.system.config.enums.ConfigFlag;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionSpecification;
import cn.hamm.spms.module.system.datapermission.service.ExportColumnMasker;
import cn.hamm.spms.module.wms.WmsServices;
import cn.hamm.spms.module.wms.input.InputEntity;
import cn.hamm.spms.module.wms.input.detail.InputDetailEntity;
import cn.hamm.spms.module.wms.input.enums.InputType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static cn.hamm.airpower.exception.Errors.FORBIDDEN;

/**
 * <h1>Service</h1>
 *
 * @author Hamm.cn
 */
@Service
public class OrderService extends AbstractBaseBillService<OrderEntity, OrderRepository, OrderDetailEntity, OrderDetailService, OrderDetailRepository> {
    private static final int DEFAULT_READ_PAGE_SIZE = 20;
    private static final int MAX_READ_PAGE_SIZE = 200;

    /**
     * 报工并发锁（Redis 分布式锁）参数。锁按订单（billId）粒度，串行化「加明细 → 读全部明细汇总
     * → 回写订单完成数」这段非原子读-算-写，避免多端并发报工互相覆盖 finishQuantity（丢更新）。
     * TTL 兜底防死锁：报工耗时远小于 30s，业务正常时 finally 必释放；即便进程崩溃，锁也会过期自解。
     */
    private static final String ORDER_SUBMIT_WORK_LOCK_PREFIX = "order_submit_work_lock_";
    private static final String ORDER_SUBMIT_WORK_LOCK_VALUE = "1";
    private static final long ORDER_SUBMIT_WORK_LOCK_TTL_SECONDS = 30;

    @Autowired(required = false)
    private OrderMapper orderMapper;

    @Autowired
    private OrderReadShadowService orderReadShadowService;

    @Autowired
    private ExportHelper exportHelper;

    @Autowired
    private MaterialService materialService;

    @Autowired
    private DataPermissionSpecification dataPermissionSpecification;

    public boolean isMapperReadEnabled() {
        return orderMapper != null;
    }

    /**
     * 生产订单分页读出口；Mapper 关闭时回退 JPA。
     */
    public @NotNull QueryPageResponse<OrderEntity> getMapperPage(QueryPageRequest<OrderEntity> request) {
        QueryPageRequest<OrderEntity> normalized = request == null ? new QueryPageRequest<>() : request;
        if (!isMapperReadEnabled()) {
            return getPage(normalized);
        }
        OrderEntity filter = normalized.getFilter() == null ? new OrderEntity() : normalized.getFilter();
        Page requestedPage = normalized.getPage() == null ? new Page() : normalized.getPage();
        int pageNum = requestedPage.getPageNum() == null ? 1 : requestedPage.getPageNum();
        int pageSize = requestedPage.getPageSize() == null
                ? DEFAULT_READ_PAGE_SIZE : requestedPage.getPageSize();
        if (pageNum < 1 || pageSize < 1) {
            throw new IllegalArgumentException("分页页码和分页大小必须为正数");
        }
        pageSize = Math.min(pageSize, MAX_READ_PAGE_SIZE);
        long offset = ((long) pageNum - 1L) * pageSize;
        if (offset > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("分页页码超出可查询范围");
        }
        orderReadShadowService.submit(filter);
        List<OrderEntity> rows = orderMapper.selectPageByPermission(filter.getBillCode(), filter.getStatus(),
                filter.getType(), (int) offset, pageSize);
        long total = orderMapper.countByPermission(filter.getBillCode(), filter.getStatus(), filter.getType());
        int totalAsInt = Math.toIntExact(total);
        int pageCount = total == 0 ? 0 : Math.toIntExact((total + pageSize - 1L) / pageSize);
        QueryPageResponse<OrderEntity> response = new QueryPageResponse<>();
        response.setList(rows);
        response.setTotal(totalAsInt);
        response.setPageCount(pageCount);
        response.setPage(new Page().setPageNum(pageNum).setPageSize(pageSize));
        response.setSort(normalized.getSort() == null ? new Sort() : normalized.getSort());
        return response;
    }

    /**
     * 生产订单详情读出口；主表通过权限链后再读取明细和关联对象。
     */
    public OrderEntity getMapperDetail(long id) {
        if (!isMapperReadEnabled()) {
            return get(id);
        }
        orderReadShadowService.submit(new OrderEntity());
        OrderEntity bill = orderMapper.selectDetailByPermission(id);
        if (bill == null) {
            return null;
        }
        if (detailService != null) {
            bill.setDetails(detailService.getAllByBillId(id));
        }
        if (bill.getMaterial() != null && bill.getMaterial().getId() != null) {
            bill.setMaterial(materialService.isMapperReadEnabled()
                    ? materialService.getMapperDetail(bill.getMaterial().getId())
                    : materialService.get(bill.getMaterial().getId()));
        }
        if (bill.getPlan() != null && bill.getPlan().getId() != null) {
            bill.setPlan(MesServices.getPlanService().getMapperDetail(bill.getPlan().getId()));
        }
        if (bill.getCustomer() != null && bill.getCustomer().getId() != null) {
            bill.setCustomer(ChannelServices.getCustomerService().get(bill.getCustomer().getId()));
        }
        return bill;
    }

    /**
     * 生产订单总数读出口；与分页使用同一筛选和权限语句。
     *
     * @param filter 业务筛选（单号/状态/类型）
     * @return 权限范围内总数
     */
    public long getMapperCount(OrderEntity filter) {
        OrderEntity normalized = filter == null ? new OrderEntity() : filter;
        if (isMapperReadEnabled()) {
            orderReadShadowService.submit(normalized);
            return orderMapper.countByPermission(normalized.getBillCode(), normalized.getStatus(), normalized.getType());
        }
        return filter(normalized).size();
    }

    /**
     * 生产订单统计读出口；只返回行数与已发布行数，与总数同一权限语句。
     *
     * @param filter 业务筛选（单号/状态/类型）
     * @return 权限范围内统计
     */
    public ReadStat getMapperStat(OrderEntity filter) {
        OrderEntity normalized = filter == null ? new OrderEntity() : filter;
        if (isMapperReadEnabled()) {
            orderReadShadowService.submit(normalized);
            return orderMapper.statByPermission(normalized.getBillCode(), normalized.getStatus(), normalized.getType());
        }
        List<OrderEntity> rows = filter(normalized);
        ReadStat stat = new ReadStat();
        stat.setTotal(rows.size());
        stat.setPublishedTotal(rows.stream().filter(row -> Boolean.TRUE.equals(row.getIsPublished())).count());
        return stat;
    }

    /**
     * 生产订单真实导出出口（Mapper）。取数走权限拦截链，遮蔽在请求线程，落盘走框架异步契约。
     *
     * @param request 导出请求（含单号/状态/类型筛选）
     * @return 导出任务文件编码，供前端 {@code queryExport} 轮询下载
     */
    public String createMapperExportTask(QueryListRequest<OrderEntity> request) {
        OrderEntity filter = request == null || request.getFilter() == null ? new OrderEntity() : request.getFilter();
        orderReadShadowService.submit(filter);
        List<OrderEntity> rows = orderMapper.selectForExport(filter.getBillCode(), filter.getStatus(), filter.getType());
        List<OrderEntity> masked = ExportColumnMasker.maskForExport(rows);
        return exportHelper.createExportTask(() -> {
            InputStream stream = CollectionUtil.toCsvInputStream(CsvFormulaSanitizer.sanitize(masked), OrderEntity.class);
            return exportHelper.saveExportFileStream(stream);
        });
    }

    @Override
    protected @NotNull List<Predicate> addSearchPredicate(
            @NotNull Root<OrderEntity> root,
            @NotNull CriteriaBuilder builder,
            @NotNull OrderEntity search
    ) {
        List<Predicate> predicateList = new ArrayList<>();

        // 叠加数据权限过滤条件
        List<Predicate> dataPermissionPredicates = dataPermissionSpecification.buildPredicates(
                root, builder, "mes.order", DataPermissionAction.QUERY
        );
        predicateList.addAll(dataPermissionPredicates);

        return predicateList;
    }

    @Override
    public IDictionary getAuditedStatus() {
        return OrderStatus.PREPARE;
    }

    @Override
    public IDictionary getAuditingStatus() {
        return OrderStatus.AUDITING;
    }

    @Override
    public IDictionary getRejectedStatus() {
        return OrderStatus.REJECTED;
    }

    @Override
    public IDictionary getBillDetailsFinishStatus() {
        return OrderStatus.INPUTTING;
    }

    @Override
    public IDictionary getFinishedStatus() {
        return OrderStatus.DONE;
    }

    /**
     * 添加订单明细
     *
     * @param orderDetail 订单明细
     */
    public void addOrderDetail(@NotNull OrderDetailEntity orderDetail) {
        ConfigService configService = SystemServices.getConfigService();
        ConfigEntity config = configService.get(ConfigFlag.ORDER_ENABLE_SUBMIT_WORK);
        FORBIDDEN.when(!config.booleanConfig(), "未开启订单报工模式");

        // 报工并发锁：见 ORDER_SUBMIT_WORK_LOCK_* 注释。锁获取失败即拒绝（fail-fast），
        // 不排队 —— 报工是低频写操作，并发冲突时让客户端重试比把请求线程挂在锁上更合理。
        String lockKey = ORDER_SUBMIT_WORK_LOCK_PREFIX + orderDetail.getBillId();
        boolean locked = tryLockSubmitWork(lockKey);
        FORBIDDEN.when(!locked, "该工单正在报工，请稍后重试");
        try {
            // 更新明细数量和状态
            Double quantity = orderDetail.getQuantity();
            Double ngQuantity = orderDetail.getNgQuantity();
            orderDetail.setQuantity(quantity)
                    .setFinishQuantity(quantity)
                    .setNgQuantity(ngQuantity)
                    .setIsFinished(true);
            OrderDetailService orderDetailService = MesServices.getOrderDetailService();
            orderDetailService.add(orderDetail);

            // 更新订单数量
            OrderEntity order = get(orderDetail.getBillId());
            List<OrderDetailEntity> details = orderDetailService.getAllByBillId(order.getId());
            double totalFinishQuantity = 0D;
            double tatalNgQuantity = 0D;
            for (OrderDetailEntity detail : details) {
                totalFinishQuantity = NumberUtil.add(totalFinishQuantity, detail.getFinishQuantity());
                tatalNgQuantity = NumberUtil.add(tatalNgQuantity, detail.getNgQuantity());
            }
            order.setFinishQuantity(totalFinishQuantity)
                    .setNgQuantity(tatalNgQuantity)
            ;
            updateToDatabase(order);

            config = configService.get(ConfigFlag.ORDER_AUTO_FINISH);
            if (config.booleanConfig() && order.getFinishQuantity() >= order.getQuantity()) {
                setBillDetailsAllFinished(order.getId());
            }
        } finally {
            releaseSubmitWorkLock(lockKey);
        }
    }

    /**
     * 尝试获取报工并发锁（Redis SET NX + TTL，Spring Data Redis 标准原语，无需额外依赖）。
     * Redis 不可用（单机调试未配 Redis）时返回 true 跳过加锁，保持改造前行为 —— 生产 Redis
     * 是数据权限/缓存的硬依赖，锁必然生效；此分支仅服务本地无 Redis 的调试场景。
     *
     * @param lockKey 锁键（按订单粒度）
     * @return 是否成功获得锁
     */
    private boolean tryLockSubmitWork(String lockKey) {
        RedisTemplate<String, Object> redisTemplate = redisHelper.getRedisTemplate();
        if (redisTemplate == null) {
            return true;
        }
        return Boolean.TRUE.equals(redisTemplate.opsForValue()
                .setIfAbsent(lockKey, ORDER_SUBMIT_WORK_LOCK_VALUE, ORDER_SUBMIT_WORK_LOCK_TTL_SECONDS, TimeUnit.SECONDS));
    }

    /**
     * 释放报工并发锁。delete 不存在键是 no-op，故 Redis 不可用时调用也安全。
     *
     * @param lockKey 锁键
     */
    private void releaseSubmitWorkLock(String lockKey) {
        redisHelper.delete(lockKey);
    }

    @Override
    protected void afterBillFinished(long billId) {
        updateToDatabase(getEntityInstance(billId)
                .setFinishTime(System.currentTimeMillis())
        );
    }

    @Override
    protected void afterAllBillDetailFinished(long billId) {
        OrderEntity orderBill = get(billId);
        if (orderBill.getFinishQuantity() == 0) {
            // 直接完成 无需入库
            orderBill.setStatus(OrderStatus.DONE.getKey())
                    .setFinishTime(System.currentTimeMillis());
            updateToDatabase(orderBill);
            return;
        }
        // 添加入库单
        addInputBill(orderBill);

        if (OrderType.PLAN.equalsKey(orderBill.getType())) {
            // 更新计划单
            MesServices.getPlanDetailService().updateDetailQuantity(
                    orderBill.getPlan().getId(),
                    orderBill.getFinishQuantity(),
                    MesServices.getPlanService(),
                    detail -> FORBIDDEN.whenNotEquals(
                            detail.getMaterial().getId(),
                            orderBill.getMaterial().getId(),
                            "物料信息不匹配")
            );
        }
    }

    /**
     * 添加入库单
     *
     * @param order 订单
     */
    private void addInputBill(OrderEntity order) {
        InputEntity input = new InputEntity();
        input.setType(InputType.PRODUCTION.getKey());
        input.setOrder(order);
        InputEntity inputSaved = WmsServices.getInputService().addAndGet(input);
        WmsServices.getInputDetailService().add(new InputDetailEntity()
                .setQuantity(order.getFinishQuantity())
                .setBillId(inputSaved.getId())
                .setMaterial(order.getMaterial())
        );
    }

    @Override
    protected @NotNull OrderEntity beforeAdd(@NotNull OrderEntity order) {
        order.setDetails(new ArrayList<>());
        return order;
    }

    @Override
    protected void afterBillAudited(long billId) {
        ConfigEntity config = SystemServices.getConfigService().get(ConfigFlag.ORDER_AUTO_START_AFTER_AUDIT);
        if (config.booleanConfig()) {
            start(billId);
        }
    }

    /**
     * 开始生产
     *
     * @param id 单据 ID
     */
    public final void start(long id) {
        OrderEntity order = get(id);
        OrderStatus[] canStartStatusList = {OrderStatus.PREPARE, OrderStatus.PAUSED};
        OrderStatus currentStatus = DictionaryUtil.getDictionary(OrderStatus.class, order.getStatus());
        FORBIDDEN.when(!Arrays.asList(canStartStatusList).contains(currentStatus), "该单据状态无法开始生产");
        updateToDatabase(getEntityInstance(id).setStatus(OrderStatus.PRODUCING.getKey()));
    }

    /**
     * 暂停生产
     *
     * @param id 单据 ID
     */
    public final void pause(long id) {
        OrderEntity order = get(id);
        OrderStatus[] canPauseStatusList = {OrderStatus.PRODUCING};
        OrderStatus currentStatus = DictionaryUtil.getDictionary(OrderStatus.class, order.getStatus());
        FORBIDDEN.when(!Arrays.asList(canPauseStatusList).contains(currentStatus), "该单据状态无法暂停生产");
        updateToDatabase(getEntityInstance(id).setStatus(OrderStatus.PAUSED.getKey()));
    }
}