package cn.hamm.spms.module.wms.output;

import cn.hamm.spms.core.CollectionUtil;
import cn.hamm.spms.core.DictionaryUtil;
import cn.hamm.spms.core.interfaces.IDictionary;
import cn.hamm.spms.framework.curd.helper.ExportHelper;
import cn.hamm.spms.framework.curd.model.query.Page;
import cn.hamm.spms.framework.curd.model.query.QueryListRequest;
import cn.hamm.spms.framework.curd.model.query.QueryPageRequest;
import cn.hamm.spms.framework.curd.model.query.QueryPageResponse;
import cn.hamm.spms.framework.curd.model.query.Sort;
import cn.hamm.spms.base.bill.AbstractBaseBillService;
import cn.hamm.spms.base.mapper.ReadStat;
import cn.hamm.spms.common.CsvFormulaSanitizer;
import cn.hamm.spms.module.channel.ChannelServices;
import cn.hamm.spms.module.system.config.enums.ConfigFlag;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionSpecification;
import cn.hamm.spms.module.system.datapermission.service.ExportColumnMasker;
import cn.hamm.spms.module.wms.WmsServices;
import cn.hamm.spms.module.wms.inventory.InventoryEntity;
import cn.hamm.spms.module.wms.inventory.InventoryService;
import cn.hamm.spms.module.wms.output.detail.OutputDetailEntity;
import cn.hamm.spms.module.wms.output.detail.OutputDetailRepository;
import cn.hamm.spms.module.wms.output.detail.OutputDetailService;
import cn.hamm.spms.module.wms.output.enums.OutputStatus;
import cn.hamm.spms.module.wms.output.enums.OutputType;
import cn.hamm.spms.module.wms.output.mapper.OutputMapper;
import cn.hamm.spms.module.wms.output.mapper.OutputReadShadowService;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static cn.hamm.spms.common.exception.Errors.FORBIDDEN;
import static cn.hamm.spms.module.system.config.enums.ConfigFlag.OUTPUT_BILL_AUTO_AUDIT;

/**
 * <h1>Service</h1>
 *
 * @author Hamm.cn
 */
@Service
public class OutputService extends AbstractBaseBillService<OutputEntity, OutputRepository, OutputDetailEntity, OutputDetailService, OutputDetailRepository> {
    private static final int DEFAULT_READ_PAGE_SIZE = 20;
    private static final int MAX_READ_PAGE_SIZE = 200;

    @Autowired(required = false)
    private OutputMapper outputMapper;

    @Autowired
    private OutputReadShadowService outputReadShadowService;

    @Autowired
    private ExportHelper exportHelper;

    @Autowired
    private DataPermissionSpecification dataPermissionSpecification;

    public boolean isMapperReadEnabled() {
        return outputMapper != null;
    }

    /**
     * 出库单分页读出口；Mapper 关闭时回退原 JPA 分页。
     */
    public @NotNull QueryPageResponse<OutputEntity> getMapperPage(QueryPageRequest<OutputEntity> request) {
        QueryPageRequest<OutputEntity> normalized = request == null ? new QueryPageRequest<>() : request;
        if (!isMapperReadEnabled()) {
            return getPage(normalized);
        }
        OutputEntity filter = normalized.getFilter() == null ? new OutputEntity() : normalized.getFilter();
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
        outputReadShadowService.submit(filter);
        List<OutputEntity> rows = outputMapper.selectPageByPermission(filter.getBillCode(), filter.getStatus(),
                filter.getType(), (int) offset, pageSize);
        long total = outputMapper.countByPermission(filter.getBillCode(), filter.getStatus(), filter.getType());
        int totalAsInt = Math.toIntExact(total);
        int pageCount = total == 0 ? 0 : Math.toIntExact((total + pageSize - 1L) / pageSize);
        QueryPageResponse<OutputEntity> response = new QueryPageResponse<>();
        response.setList(rows);
        response.setTotal(totalAsInt);
        response.setPageCount(pageCount);
        response.setPage(new Page().setPageNum(pageNum).setPageSize(pageSize));
        response.setSort(normalized.getSort() == null ? new Sort() : normalized.getSort());
        return response;
    }

    /**
     * 出库单详情读出口；主表先过 Mapper 权限，再加载明细和关联对象。
     */
    public OutputEntity getMapperDetail(long id) {
        if (!isMapperReadEnabled()) {
            return get(id);
        }
        outputReadShadowService.submit(new OutputEntity());
        OutputEntity bill = outputMapper.selectDetailByPermission(id);
        if (bill == null) {
            return null;
        }
        if (detailService != null) {
            bill.setDetails(detailService.getAllByBillId(id));
        }
        if (bill.getSale() != null && bill.getSale().getId() != null) {
            bill.setSale(ChannelServices.getSaleService().get(bill.getSale().getId()));
        }
        if (bill.getMove() != null && bill.getMove().getId() != null) {
            bill.setMove(WmsServices.getMoveService().getMapperDetail(bill.getMove().getId()));
        }
        return bill;
    }

    /**
     * 出库单总数读出口；与分页使用同一筛选和权限语句。
     *
     * @param filter 业务筛选（单号/状态/类型）
     * @return 权限范围内总数
     */
    public long getMapperCount(OutputEntity filter) {
        OutputEntity normalized = filter == null ? new OutputEntity() : filter;
        if (isMapperReadEnabled()) {
            outputReadShadowService.submit(normalized);
            return outputMapper.countByPermission(normalized.getBillCode(), normalized.getStatus(), normalized.getType());
        }
        return filter(normalized).size();
    }

    /**
     * 出库单统计读出口；只返回行数与已发布行数，与总数同一权限语句。
     *
     * @param filter 业务筛选（单号/状态/类型）
     * @return 权限范围内统计
     */
    public ReadStat getMapperStat(OutputEntity filter) {
        OutputEntity normalized = filter == null ? new OutputEntity() : filter;
        if (isMapperReadEnabled()) {
            outputReadShadowService.submit(normalized);
            return outputMapper.statByPermission(normalized.getBillCode(), normalized.getStatus(), normalized.getType());
        }
        List<OutputEntity> rows = filter(normalized);
        ReadStat stat = new ReadStat();
        stat.setTotal(rows.size());
        stat.setPublishedTotal(rows.stream().filter(row -> Boolean.TRUE.equals(row.getIsPublished())).count());
        return stat;
    }

    /**
     * 出库单真实导出出口（Mapper）。与入库单同模板：取数走权限拦截链，遮蔽在请求线程，落盘走框架异步契约。
     *
     * @param request 导出请求（含单号/状态/类型筛选）
     * @return 导出任务文件编码，供前端 {@code queryExport} 轮询下载
     */
    public String createMapperExportTask(QueryListRequest<OutputEntity> request) {
        OutputEntity filter = request == null || request.getFilter() == null ? new OutputEntity() : request.getFilter();
        outputReadShadowService.submit(filter);
        List<OutputEntity> rows = outputMapper.selectForExport(filter.getBillCode(), filter.getStatus(), filter.getType());
        List<OutputEntity> masked = ExportColumnMasker.maskForExport(rows);
        return exportHelper.createExportTask(() -> {
            InputStream stream = CollectionUtil.toCsvInputStream(CsvFormulaSanitizer.sanitize(masked), OutputEntity.class);
            return exportHelper.saveExportFileStream(stream);
        });
    }

    @Override
    protected @NotNull List<Predicate> addSearchPredicate(
            @NotNull Root<OutputEntity> root,
            @NotNull CriteriaBuilder builder,
            @NotNull OutputEntity search
    ) {
        List<Predicate> predicateList = new ArrayList<>();
        List<Predicate> dataPermissionPredicates = dataPermissionSpecification.buildPredicates(
                root, builder, "wms.output", DataPermissionAction.QUERY
        );
        predicateList.addAll(dataPermissionPredicates);
        return predicateList;
    }

    @Override
    public IDictionary getAuditedStatus() {
        return OutputStatus.OUTPUTTING;
    }

    @Override
    public IDictionary getAuditingStatus() {
        return OutputStatus.AUDITING;
    }

    @Override
    public IDictionary getRejectedStatus() {
        return OutputStatus.REJECTED;
    }

    @Override
    public IDictionary getBillDetailsFinishStatus() {
        return OutputStatus.DONE;
    }

    @Override
    protected void afterBillFinished(long billId) {
        OutputEntity outputBill = get(billId);
        OutputType outputType = DictionaryUtil.getDictionary(OutputType.class, outputBill.getType());
        switch (outputType) {
            case SALE -> ChannelServices.getSaleService().setBillFinished(outputBill.getSale().getId());
            default -> {
            }
        }
    }

    @Override
    protected void afterDetailFinishAdded(long detailId, @NotNull OutputDetailEntity outputDetail) {
        InventoryService inventoryService = WmsServices.getInventoryService();

        // 出库明细
        OutputDetailEntity existDetail = detailService.get(outputDetail.getId());

        // 物料 ID
        Long materialId = existDetail.getMaterial().getId();

        // 出库单
        OutputEntity bill = get(existDetail.getBillId());

        // 库存信息
        InventoryEntity inventory = inventoryService.get(outputDetail.getInventory().getId());
        FORBIDDEN.whenNotEquals(inventory.getMaterial().getId(), materialId, "物料信息不匹配");
        transactionHelper.run(() -> {
            Double outputDetailQuantity = outputDetail.getQuantity();
            inventoryService.reduceInventoryQuantity(inventory.getId(), outputDetailQuantity);
            // 获取出库单类型
            OutputType outputType = DictionaryUtil.getDictionary(OutputType.class, bill.getType());
            switch (outputType) {
                case SALE -> ChannelServices.getSaleDetailService().updateDetailQuantity(
                        bill.getSale().getId(),
                        outputDetailQuantity,
                        ChannelServices.getSaleService(),
                        detail -> FORBIDDEN.whenNotEquals(
                                detail.getMaterial().getId(),
                                materialId,
                                "物料信息不匹配"));
                default -> {
                }
            }
        });
    }

    @Override
    protected ConfigFlag getAutoAuditConfigFlag() {
        return OUTPUT_BILL_AUTO_AUDIT;
    }
}
