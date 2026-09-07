package cn.hamm.spms.module.wms.input;

import cn.hamm.airpower.core.CollectionUtil;
import cn.hamm.airpower.core.DictionaryUtil;
import cn.hamm.airpower.core.ReflectUtil;
import cn.hamm.airpower.core.interfaces.IDictionary;
import cn.hamm.airpower.curd.helper.ExportHelper;
import cn.hamm.airpower.curd.model.query.Page;
import cn.hamm.airpower.curd.model.query.QueryListRequest;
import cn.hamm.airpower.curd.model.query.QueryPageRequest;
import cn.hamm.airpower.curd.model.query.QueryPageResponse;
import cn.hamm.airpower.curd.model.query.Sort;
import cn.hamm.spms.base.bill.AbstractBaseBillService;
import cn.hamm.spms.base.mapper.ReadStat;
import cn.hamm.spms.module.channel.ChannelServices;
import cn.hamm.spms.module.factory.storage.StorageEntity;
import cn.hamm.spms.module.mes.MesServices;
import cn.hamm.spms.module.system.config.enums.ConfigFlag;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionSpecification;
import cn.hamm.spms.module.system.datapermission.service.ExportColumnMasker;
import cn.hamm.spms.module.wms.WmsServices;
import cn.hamm.spms.module.wms.input.detail.InputDetailEntity;
import cn.hamm.spms.module.wms.input.detail.InputDetailRepository;
import cn.hamm.spms.module.wms.input.detail.InputDetailService;
import cn.hamm.spms.module.wms.input.enums.InputStatus;
import cn.hamm.spms.module.wms.input.mapper.InputMapper;
import cn.hamm.spms.module.wms.input.mapper.InputReadShadowService;
import cn.hamm.spms.module.wms.input.enums.InputType;
import cn.hamm.spms.module.wms.inventory.InventoryEntity;
import cn.hamm.spms.module.wms.inventory.InventoryService;
import cn.hamm.spms.module.wms.inventory.enums.InventoryType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static cn.hamm.airpower.exception.Errors.FORBIDDEN;
import static cn.hamm.spms.module.system.config.enums.ConfigFlag.INPUT_BILL_AUTO_AUDIT;

/**
 * <h1>Service</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
@Service
public class InputService extends AbstractBaseBillService<InputEntity, InputRepository, InputDetailEntity, InputDetailService, InputDetailRepository> {
    private static final int DEFAULT_READ_PAGE_SIZE = 20;
    private static final int MAX_READ_PAGE_SIZE = 200;

    @Autowired(required = false)
    private InputMapper inputMapper;

    @Autowired
    private InputReadShadowService inputReadShadowService;

    @Autowired
    private ExportHelper exportHelper;

    @Autowired
    private DataPermissionSpecification dataPermissionSpecification;

    /**
     * @return 是否已启用入库单 Mapper 读路径
     */
    public boolean isMapperReadEnabled() {
        return inputMapper != null;
    }

    /**
     * 入库单分页读出口；写和明细保存仍走原 JPA 单据服务。
     */
    public @NotNull QueryPageResponse<InputEntity> getMapperPage(QueryPageRequest<InputEntity> request) {
        QueryPageRequest<InputEntity> normalized = request == null ? new QueryPageRequest<>() : request;
        if (!isMapperReadEnabled()) {
            return getPage(normalized);
        }
        InputEntity filter = normalized.getFilter() == null ? new InputEntity() : normalized.getFilter();
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
        inputReadShadowService.submit(filter);
        List<InputEntity> rows = inputMapper.selectPageByPermission(filter.getBillCode(), filter.getStatus(),
                filter.getType(), (int) offset, pageSize);
        long total = inputMapper.countByPermission(filter.getBillCode(), filter.getStatus(), filter.getType());
        int totalAsInt = Math.toIntExact(total);
        int pageCount = total == 0 ? 0 : Math.toIntExact((total + pageSize - 1L) / pageSize);
        Sort sort = normalized.getSort() == null ? new Sort() : normalized.getSort();
        QueryPageResponse<InputEntity> response = new QueryPageResponse<>();
        response.setList(rows);
        response.setTotal(totalAsInt);
        response.setPageCount(pageCount);
        response.setPage(new Page().setPageNum(pageNum).setPageSize(pageSize));
        response.setSort(sort);
        return response;
    }

    /**
     * 入库单详情读出口；加载明细后再按原单据服务语义补齐关联单据。
     */
    public InputEntity getMapperDetail(long id) {
        if (!isMapperReadEnabled()) {
            return get(id);
        }
        inputReadShadowService.submit(new InputEntity());
        InputEntity bill = inputMapper.selectDetailByPermission(id);
        if (bill == null) {
            return null;
        }
        if (detailService != null) {
            bill.setDetails(detailService.getAllByBillId(id));
        }
        hydrateRelations(bill);
        return bill;
    }

    /**
     * 入库单总数读出口；与分页使用同一筛选和权限语句。
     *
     * @param filter 业务筛选（单号/状态/类型）
     * @return 权限范围内总数
     */
    public long getMapperCount(InputEntity filter) {
        InputEntity normalized = filter == null ? new InputEntity() : filter;
        if (isMapperReadEnabled()) {
            inputReadShadowService.submit(normalized);
            return inputMapper.countByPermission(normalized.getBillCode(), normalized.getStatus(), normalized.getType());
        }
        return filter(normalized).size();
    }

    /**
     * 入库单统计读出口；只返回行数与已发布行数，与总数同一权限语句。
     *
     * @param filter 业务筛选（单号/状态/类型）
     * @return 权限范围内统计
     */
    public ReadStat getMapperStat(InputEntity filter) {
        InputEntity normalized = filter == null ? new InputEntity() : filter;
        if (isMapperReadEnabled()) {
            inputReadShadowService.submit(normalized);
            return inputMapper.statByPermission(normalized.getBillCode(), normalized.getStatus(), normalized.getType());
        }
        List<InputEntity> rows = filter(normalized);
        ReadStat stat = new ReadStat();
        stat.setTotal(rows.size());
        stat.setPublishedTotal(rows.stream().filter(row -> Boolean.TRUE.equals(row.getIsPublished())).count());
        return stat;
    }

    /**
     * 入库单真实导出出口（Mapper）。
     *
     * <p>行集经 {@link InputMapper#selectForExport} 走与分页/详情/总数相同的权限拦截链，列级遮蔽在
     * <b>请求线程</b>完成后交给 {@link ExportHelper#createExportTask} 落盘，保留异步下载契约。
     * 入库单无 {@code @SensitiveField}，{@link ExportColumnMasker#maskForExport} 不改变行内容，
     * 但保留同一份遮蔽实现以防后续在明细/单据上新增敏感列。</p>
     *
     * @param request 导出请求（含单号/状态/类型筛选）
     * @return 导出任务文件编码，供前端 {@code queryExport} 轮询下载
     */
    public String createMapperExportTask(QueryListRequest<InputEntity> request) {
        InputEntity filter = request == null || request.getFilter() == null ? new InputEntity() : request.getFilter();
        inputReadShadowService.submit(filter);
        List<InputEntity> rows = inputMapper.selectForExport(filter.getBillCode(), filter.getStatus(), filter.getType());
        List<InputEntity> masked = ExportColumnMasker.maskForExport(rows);
        return exportHelper.createExportTask(() -> {
            InputStream stream = CollectionUtil.toCsvInputStream(masked, InputEntity.class);
            return exportHelper.saveExportFileStream(stream);
        });
    }

    /**
     * 详情的关联对象不通过入库 SQL JOIN；每个关联资源沿自己的服务边界读取。
     */
    private void hydrateRelations(InputEntity bill) {
        if (bill.getPurchase() != null && bill.getPurchase().getId() != null) {
            bill.setPurchase(ChannelServices.getPurchaseService().get(bill.getPurchase().getId()));
        }
        if (bill.getStructure() != null && bill.getStructure().getId() != null) {
            bill.setStructure(cn.hamm.spms.module.factory.FactoryServices.getStructureService()
                    .get(bill.getStructure().getId()));
        }
        if (bill.getOrder() != null && bill.getOrder().getId() != null) {
            bill.setOrder(MesServices.getOrderService().getMapperDetail(bill.getOrder().getId()));
        }
        if (bill.getMove() != null && bill.getMove().getId() != null) {
            bill.setMove(WmsServices.getMoveService().getMapperDetail(bill.getMove().getId()));
        }
    }

    @Override
    protected @NotNull List<Predicate> addSearchPredicate(
            @NotNull Root<InputEntity> root,
            @NotNull CriteriaBuilder builder,
            @NotNull InputEntity search
    ) {
        List<Predicate> predicateList = new ArrayList<>();
        List<Predicate> dataPermissionPredicates = dataPermissionSpecification.buildPredicates(
                root, builder, "wms.input", DataPermissionAction.QUERY
        );
        predicateList.addAll(dataPermissionPredicates);
        return predicateList;
    }

    @Override
    public IDictionary getAuditingStatus() {
        return InputStatus.AUDITING;
    }

    @Override
    public IDictionary getAuditedStatus() {
        return InputStatus.INPUTTING;
    }

    @Override
    public IDictionary getRejectedStatus() {
        return InputStatus.REJECTED;
    }

    @Override
    public IDictionary getBillDetailsFinishStatus() {
        return InputStatus.DONE;
    }

    @Override
    protected void afterBillFinished(long billId) {
        InputEntity inputBill = get(billId);
        InputType inputType = DictionaryUtil.getDictionary(InputType.class, inputBill.getType());
        log.info("入库单入库完成 {}，单据ID:{} {} 入库类型 {}",
                ReflectUtil.getDescription(getFirstParameterizedTypeClass()),
                billId,
                inputBill.getBillCode(),
                inputType.getLabel()
        );
        switch (inputType) {
            case PURCHASE -> ChannelServices.getPurchaseService().setBillFinished(inputBill.getPurchase().getId());
            case PRODUCTION -> MesServices.getOrderService().setBillFinished(inputBill.getOrder().getId());
            default -> {
            }
        }
    }

    @Override
    protected void afterDetailFinishAdded(long detailId, @NotNull InputDetailEntity inputDetail) {
        // 入库仓库信息
        StorageEntity storage = inputDetail.getStorage();
        if (Objects.isNull(storage) || Objects.isNull(storage.getId())) {
            FORBIDDEN.show("请传入入库仓库");
            return;
        }
        InputDetailEntity existDetail = detailService.get(inputDetail.getId());
        InventoryService inventoryService = WmsServices.getInventoryService();

        // 查询库存信息
        InventoryEntity inventory = inventoryService.getByMaterialIdAndStorageId(existDetail.getMaterial().getId(), storage.getId());

        // 入库数量
        Double inputDetailQuantity = inputDetail.getQuantity();

        if (Objects.nonNull(inventory)) {
            inventoryService.addInventoryQuantity(inventory.getId(), inputDetailQuantity);
            log.info("入库单明细更新库存完毕，单据ID: {}", inputDetail.getId());
            return;
        }
        inventory = new InventoryEntity()
                .setQuantity(inputDetailQuantity)
                .setMaterial(existDetail.getMaterial())
                .setStorage(storage)
                .setType(InventoryType.STORAGE.getKey());
        inventoryService.add(inventory);
        log.info("入库单明细创建库存完毕，单据ID: {}", inputDetail.getId());
    }

    @Override
    protected ConfigFlag getAutoAuditConfigFlag() {
        return INPUT_BILL_AUTO_AUDIT;
    }
}
