package cn.hamm.spms.module.mes.picking;

import cn.hamm.airpower.core.CollectionUtil;
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
import cn.hamm.spms.module.mes.picking.detail.PickingDetailEntity;
import cn.hamm.spms.module.mes.picking.detail.PickingDetailRepository;
import cn.hamm.spms.module.mes.picking.detail.PickingDetailService;
import cn.hamm.spms.module.mes.picking.enums.PickingStatus;
import cn.hamm.spms.module.mes.picking.mapper.PickingMapper;
import cn.hamm.spms.module.mes.picking.mapper.PickingReadShadowService;
import cn.hamm.spms.module.mes.MesServices;
import cn.hamm.spms.module.system.config.enums.ConfigFlag;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionSpecification;
import cn.hamm.spms.module.system.datapermission.service.ExportColumnMasker;
import cn.hamm.spms.module.wms.WmsServices;
import cn.hamm.spms.module.wms.inventory.InventoryEntity;
import cn.hamm.spms.module.wms.inventory.InventoryService;
import cn.hamm.spms.module.wms.inventory.enums.InventoryType;
import cn.hamm.spms.module.wms.output.OutputEntity;
import cn.hamm.spms.module.wms.output.detail.OutputDetailEntity;
import cn.hamm.spms.module.wms.output.enums.OutputType;
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

import static cn.hamm.spms.module.system.config.enums.ConfigFlag.PICKING_BILL_AUTO_AUDIT;

/**
 * <h1>Service</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
@Service
public class PickingService extends AbstractBaseBillService<PickingEntity, PickingRepository, PickingDetailEntity, PickingDetailService, PickingDetailRepository> {
    private static final int DEFAULT_READ_PAGE_SIZE = 20;
    private static final int MAX_READ_PAGE_SIZE = 200;

    @Autowired(required = false)
    private PickingMapper pickingMapper;

    @Autowired
    private PickingReadShadowService pickingReadShadowService;

    @Autowired
    private ExportHelper exportHelper;

    @Autowired
    private DataPermissionSpecification dataPermissionSpecification;

    public boolean isMapperReadEnabled() {
        return pickingMapper != null;
    }

    /**
     * 领料单分页读出口；Mapper 关闭时回退 JPA。
     */
    public @NotNull QueryPageResponse<PickingEntity> getMapperPage(QueryPageRequest<PickingEntity> request) {
        QueryPageRequest<PickingEntity> normalized = request == null ? new QueryPageRequest<>() : request;
        if (!isMapperReadEnabled()) {
            return getPage(normalized);
        }
        PickingEntity filter = normalized.getFilter() == null ? new PickingEntity() : normalized.getFilter();
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
        pickingReadShadowService.submit(filter);
        List<PickingEntity> rows = pickingMapper.selectPageByPermission(filter.getBillCode(), filter.getStatus(),
                filter.getType(), (int) offset, pageSize);
        long total = pickingMapper.countByPermission(filter.getBillCode(), filter.getStatus(), filter.getType());
        int totalAsInt = Math.toIntExact(total);
        int pageCount = total == 0 ? 0 : Math.toIntExact((total + pageSize - 1L) / pageSize);
        QueryPageResponse<PickingEntity> response = new QueryPageResponse<>();
        response.setList(rows);
        response.setTotal(totalAsInt);
        response.setPageCount(pageCount);
        response.setPage(new Page().setPageNum(pageNum).setPageSize(pageSize));
        response.setSort(normalized.getSort() == null ? new Sort() : normalized.getSort());
        return response;
    }

    /**
     * 领料单详情读出口；主表通过权限链后再读取明细和关联对象。
     */
    public PickingEntity getMapperDetail(long id) {
        if (!isMapperReadEnabled()) {
            return get(id);
        }
        pickingReadShadowService.submit(new PickingEntity());
        PickingEntity bill = pickingMapper.selectDetailByPermission(id);
        if (bill == null) {
            return null;
        }
        if (detailService != null) {
            bill.setDetails(detailService.getAllByBillId(id));
        }
        if (bill.getStructure() != null && bill.getStructure().getId() != null) {
            bill.setStructure(cn.hamm.spms.module.factory.FactoryServices.getStructureService()
                    .get(bill.getStructure().getId()));
        }
        if (bill.getOrder() != null && bill.getOrder().getId() != null) {
            bill.setOrder(MesServices.getOrderService().getMapperDetail(bill.getOrder().getId()));
        }
        return bill;
    }

    /**
     * 领料单总数读出口；与分页使用同一筛选和权限语句。
     *
     * @param filter 业务筛选（单号/状态/类型）
     * @return 权限范围内总数
     */
    public long getMapperCount(PickingEntity filter) {
        PickingEntity normalized = filter == null ? new PickingEntity() : filter;
        if (isMapperReadEnabled()) {
            pickingReadShadowService.submit(normalized);
            return pickingMapper.countByPermission(normalized.getBillCode(), normalized.getStatus(), normalized.getType());
        }
        return filter(normalized).size();
    }

    /**
     * 领料单统计读出口；只返回行数与已发布行数，与总数同一权限语句。
     *
     * @param filter 业务筛选（单号/状态/类型）
     * @return 权限范围内统计
     */
    public ReadStat getMapperStat(PickingEntity filter) {
        PickingEntity normalized = filter == null ? new PickingEntity() : filter;
        if (isMapperReadEnabled()) {
            pickingReadShadowService.submit(normalized);
            return pickingMapper.statByPermission(normalized.getBillCode(), normalized.getStatus(), normalized.getType());
        }
        List<PickingEntity> rows = filter(normalized);
        ReadStat stat = new ReadStat();
        stat.setTotal(rows.size());
        stat.setPublishedTotal(rows.stream().filter(row -> Boolean.TRUE.equals(row.getIsPublished())).count());
        return stat;
    }

    /**
     * 领料单真实导出出口（Mapper）。取数走权限拦截链，遮蔽在请求线程，落盘走框架异步契约。
     *
     * @param request 导出请求（含单号/状态/类型筛选）
     * @return 导出任务文件编码，供前端 {@code queryExport} 轮询下载
     */
    public String createMapperExportTask(QueryListRequest<PickingEntity> request) {
        PickingEntity filter = request == null || request.getFilter() == null ? new PickingEntity() : request.getFilter();
        pickingReadShadowService.submit(filter);
        List<PickingEntity> rows = pickingMapper.selectForExport(filter.getBillCode(), filter.getStatus(), filter.getType());
        List<PickingEntity> masked = ExportColumnMasker.maskForExport(rows);
        return exportHelper.createExportTask(() -> {
            InputStream stream = CollectionUtil.toCsvInputStream(masked, PickingEntity.class);
            return exportHelper.saveExportFileStream(stream);
        });
    }

    @Override
    protected @NotNull List<Predicate> addSearchPredicate(
            @NotNull Root<PickingEntity> root,
            @NotNull CriteriaBuilder builder,
            @NotNull PickingEntity search
    ) {
        List<Predicate> predicateList = new ArrayList<>();
        List<Predicate> dataPermissionPredicates = dataPermissionSpecification.buildPredicates(
                root, builder, "mes.picking", DataPermissionAction.QUERY
        );
        predicateList.addAll(dataPermissionPredicates);
        return predicateList;
    }

    @Override
    public IDictionary getAuditingStatus() {
        return PickingStatus.AUDITING;
    }

    @Override
    public IDictionary getAuditedStatus() {
        return PickingStatus.OUTPUTTING;
    }

    @Override
    public IDictionary getRejectedStatus() {
        return PickingStatus.REJECTED;
    }

    @Override
    public IDictionary getBillDetailsFinishStatus() {
        return PickingStatus.DONE;
    }

    @Override
    protected ConfigFlag getAutoAuditConfigFlag() {
        return PICKING_BILL_AUTO_AUDIT;
    }

    @Override
    protected void afterBillAudited(long billId) {
        // 创建领料出库单
        List<OutputDetailEntity> details = new ArrayList<>();
        detailService.getAllByBillId(billId)
                .forEach(detail -> details.add(
                        new OutputDetailEntity()
                                .setQuantity(detail.getQuantity())
                                .setMaterial(detail.getMaterial())
                ));
        OutputEntity outputBill = new OutputEntity()
                .setPicking(get(billId))
                .setType(OutputType.PICKING.getKey())
                .setDetails(details);
        WmsServices.getOutputService().add(outputBill);
    }

    @Override
    protected void afterAllBillDetailFinished(long billId) {
        log.info("领料单所有明细都已完成，单据ID:{}", billId);
        PickingEntity pickingBill = get(billId);
        // 添加线边库存
        List<PickingDetailEntity> details = detailService.getAllByBillId(pickingBill.getId());
        InventoryService inventoryService = WmsServices.getInventoryService();
        details.forEach(detail -> {
            // 查询库存信息
            InventoryEntity inventory = inventoryService.getByMaterialIdAndStructureId(detail.getMaterial().getId(), pickingBill.getStructure().getId());
            if (Objects.nonNull(inventory)) {
                inventory.setQuantity(NumberUtil.add(inventory.getQuantity(), detail.getFinishQuantity()));
                inventoryService.updateToDatabase(inventory);
                return;
            }
            inventory = new InventoryEntity()
                    .setQuantity(detail.getFinishQuantity())
                    .setMaterial(detail.getMaterial())
                    .setStructure(pickingBill.getStructure())
                    .setType(InventoryType.STRUCTURE.getKey());
            inventoryService.add(inventory);
        });
    }
}
