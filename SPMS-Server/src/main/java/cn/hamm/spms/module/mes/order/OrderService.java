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
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
            InputStream stream = CollectionUtil.toCsvInputStream(masked, OrderEntity.class);
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

        //todo 需要加锁更新

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