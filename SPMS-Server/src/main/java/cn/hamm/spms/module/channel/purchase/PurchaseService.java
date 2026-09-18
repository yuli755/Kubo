package cn.hamm.spms.module.channel.purchase;

import cn.hamm.spms.core.CollectionUtil;
import cn.hamm.spms.core.NumberUtil;
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
import cn.hamm.spms.module.channel.purchase.detail.PurchaseDetailEntity;
import cn.hamm.spms.module.channel.purchase.detail.PurchaseDetailRepository;
import cn.hamm.spms.module.channel.purchase.detail.PurchaseDetailService;
import cn.hamm.spms.module.channel.purchase.enums.PurchaseStatus;
import cn.hamm.spms.module.channel.purchase.mapper.PurchaseMapper;
import cn.hamm.spms.base.config.ConfigFlag;
import cn.hamm.spms.framework.curd.datapermission.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionSpecification;
import cn.hamm.spms.module.system.datapermission.service.ExportColumnMasker;
import cn.hamm.spms.module.wms.WmsServices;
import cn.hamm.spms.module.wms.input.InputEntity;
import cn.hamm.spms.module.wms.input.detail.InputDetailEntity;
import cn.hamm.spms.module.wms.input.enums.InputStatus;
import cn.hamm.spms.module.wms.input.enums.InputType;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static cn.hamm.spms.base.config.ConfigFlag.PURCHASE_BILL_AUTO_AUDIT;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

/**
 * <h1>Service</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
@Service
public class PurchaseService extends AbstractBaseBillService<
        PurchaseEntity, PurchaseRepository,
        PurchaseDetailEntity, PurchaseDetailService, PurchaseDetailRepository
        > {
    @Autowired
    private DataPermissionSpecification dataPermissionSpecification;

    @Autowired(required = false)
    private PurchaseMapper purchaseMapper;

    @Autowired
    private ExportHelper exportHelper;

    private static final int DEFAULT_READ_PAGE_SIZE = 20;
    private static final int MAX_READ_PAGE_SIZE = 200;

    /**
     * @return 是否已启用采购单 Mapper 读路径
     */
    public boolean isMapperReadEnabled() {
        return purchaseMapper != null;
    }

    /**
     * 采购单分页读出口；写和明细保存仍走原 JPA 单据服务。
     */
    public @NotNull QueryPageResponse<PurchaseEntity> getMapperPage(QueryPageRequest<PurchaseEntity> request) {
        QueryPageRequest<PurchaseEntity> normalized = request == null ? new QueryPageRequest<>() : request;
        if (!isMapperReadEnabled()) {
            return getPage(normalized);
        }
        PurchaseEntity filter = normalized.getFilter() == null ? new PurchaseEntity() : normalized.getFilter();
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
        List<PurchaseEntity> rows = purchaseMapper.selectPageByPermission(filter.getBillCode(), filter.getReason(),
                filter.getStatus(), (int) offset, pageSize);
        long total = purchaseMapper.countByPermission(filter.getBillCode(), filter.getReason(), filter.getStatus());
        int totalAsInt = Math.toIntExact(total);
        int pageCount = total == 0 ? 0 : Math.toIntExact((total + pageSize - 1L) / pageSize);
        Sort sort = normalized.getSort() == null ? new Sort() : normalized.getSort();
        QueryPageResponse<PurchaseEntity> response = new QueryPageResponse<>();
        response.setList(rows);
        response.setTotal(totalAsInt);
        response.setPageCount(pageCount);
        response.setPage(new Page().setPageNum(pageNum).setPageSize(pageSize));
        response.setSort(sort);
        return response;
    }

    /**
     * 采购单详情读出口；加载明细（采购单无跨资源关联主表）。
     */
    public PurchaseEntity getMapperDetail(long id) {
        if (!isMapperReadEnabled()) {
            return get(id);
        }
        PurchaseEntity bill = purchaseMapper.selectDetailByPermission(id);
        if (bill == null) {
            return null;
        }
        if (detailService != null) {
            bill.setDetails(detailService.getAllByBillId(id));
        }
        return bill;
    }

    /**
     * 采购单总数读出口；与分页使用同一筛选和权限语句。
     *
     * @param filter 业务筛选（单号/事由/状态）
     * @return 权限范围内总数
     */
    public long getMapperCount(PurchaseEntity filter) {
        PurchaseEntity normalized = filter == null ? new PurchaseEntity() : filter;
        if (isMapperReadEnabled()) {
            return purchaseMapper.countByPermission(normalized.getBillCode(), normalized.getReason(), normalized.getStatus());
        }
        return filter(normalized).size();
    }

    /**
     * 采购单统计读出口；只返回行数与已发布行数，与总数同一权限语句。
     *
     * @param filter 业务筛选（单号/事由/状态）
     * @return 权限范围内统计
     */
    public ReadStat getMapperStat(PurchaseEntity filter) {
        PurchaseEntity normalized = filter == null ? new PurchaseEntity() : filter;
        if (isMapperReadEnabled()) {
            return purchaseMapper.statByPermission(normalized.getBillCode(), normalized.getReason(), normalized.getStatus());
        }
        List<PurchaseEntity> rows = filter(normalized);
        ReadStat stat = new ReadStat();
        stat.setTotal(rows.size());
        stat.setPublishedTotal(rows.stream().filter(row -> Boolean.TRUE.equals(row.getIsPublished())).count());
        return stat;
    }

    /**
     * 采购单真实导出出口（Mapper）。
     *
     * <p>行集经 {@link PurchaseMapper#selectForExport} 走与分页/详情/总数相同的权限拦截链，列级遮蔽在
     * <b>请求线程</b>完成后交给 {@link ExportHelper#createExportTask} 落盘，保留异步下载契约。
     * 采购单无 {@code @SensitiveField}，{@link ExportColumnMasker#maskForExport} 不改变行内容，
     * 但保留同一份遮蔽实现以防后续新增敏感列。</p>
     *
     * @param request 导出请求（含单号/事由/状态筛选）
     * @return 导出任务文件编码，供前端 {@code queryExport} 轮询下载
     */
    public String createMapperExportTask(QueryListRequest<PurchaseEntity> request) {
        PurchaseEntity filter = request == null || request.getFilter() == null ? new PurchaseEntity() : request.getFilter();
        List<PurchaseEntity> rows = purchaseMapper.selectForExport(filter.getBillCode(), filter.getReason(), filter.getStatus());
        List<PurchaseEntity> masked = ExportColumnMasker.maskForExport(rows);
        return exportHelper.createExportTask(() -> {
            InputStream stream = CollectionUtil.toCsvInputStream(CsvFormulaSanitizer.sanitize(masked), PurchaseEntity.class);
            return exportHelper.saveExportFileStream(stream);
        });
    }

    @Override
    protected void addSearchPredicate(@NotNull QueryWrapper<PurchaseEntity> wrapper, @NotNull PurchaseEntity search) {
        // 数据权限：渲染器产出的 SQL 片段直接挂到 wrapper 上（J2 起不再编 JPA Criteria）。
        // 引擎把这个钩子接在通用条件之后，所以片段与用户条件天然是 AND 关系。
        dataPermissionSpecification.applyQuery(wrapper, getEntityClass(), "channel.purchase", DataPermissionAction.QUERY);
    }

    @Override
    public IDictionary getAuditingStatus() {
        return PurchaseStatus.AUDITING;
    }

    @Override
    public IDictionary getAuditedStatus() {
        return PurchaseStatus.PURCHASING;
    }

    @Override
    public IDictionary getRejectedStatus() {
        return PurchaseStatus.REJECTED;
    }

    @Override
    public IDictionary getBillDetailsFinishStatus() {
        return PurchaseStatus.INPUTTING;
    }

    @Override
    public IDictionary getFinishedStatus() {
        return PurchaseStatus.DONE;
    }

    @Override
    protected void afterAllBillDetailFinished(long billId) {
        PurchaseEntity purchaseBill = get(billId);
        List<PurchaseDetailEntity> details = detailService.getAllByBillId(billId);
        List<InputDetailEntity> inputDetails = new ArrayList<>();
        double totalRealPrice = 0D;
        for (PurchaseDetailEntity detail : details) {
            totalRealPrice += detail.getPrice() * detail.getFinishQuantity();
            inputDetails.add(new InputDetailEntity()
                    .setQuantity(detail.getFinishQuantity())
                    .setMaterial(detail.getMaterial())
            );
        }
        purchaseBill.setTotalRealPrice(totalRealPrice);
        updateToDatabase(purchaseBill);
        log.info("采购单已经全部采购完成，单据ID:{}", purchaseBill.getId());

        // 创建采购入库单
        InputEntity inputBill = new InputEntity()
                .setStatus(InputStatus.AUDITING.getKey())
                .setPurchase(purchaseBill)
                .setType(InputType.PURCHASE.getKey())
                .setDetails(inputDetails);
        WmsServices.getInputService().add(inputBill);
        log.info("创建采购入库单：{}", inputBill);
    }

    @Override
    protected void afterDetailSaved(long purchaseId) {
        List<PurchaseDetailEntity> details = detailService.getAllByBillId(purchaseId);
        double totalPrice = details.stream()
                .mapToDouble(detail -> NumberUtil.multiply(detail.getPrice(), detail.getQuantity()))
                .sum();
        updateToDatabase(getEntityInstance(purchaseId).setTotalPrice(totalPrice));
    }

    @Override
    protected ConfigFlag getAutoAuditConfigFlag() {
        return PURCHASE_BILL_AUTO_AUDIT;
    }
}
