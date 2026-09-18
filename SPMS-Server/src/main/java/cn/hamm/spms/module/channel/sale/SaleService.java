package cn.hamm.spms.module.channel.sale;

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
import cn.hamm.spms.module.channel.ChannelServices;
import cn.hamm.spms.module.channel.sale.detail.SaleDetailEntity;
import cn.hamm.spms.module.channel.sale.detail.SaleDetailRepository;
import cn.hamm.spms.module.channel.sale.detail.SaleDetailService;
import cn.hamm.spms.module.channel.sale.enums.SaleStatus;
import cn.hamm.spms.module.channel.sale.mapper.SaleMapper;
import cn.hamm.spms.module.channel.sale.mapper.SaleReadShadowService;
import cn.hamm.spms.base.config.ConfigFlag;
import cn.hamm.spms.framework.curd.datapermission.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionSpecification;
import cn.hamm.spms.module.system.datapermission.service.ExportColumnMasker;
import cn.hamm.spms.module.wms.WmsServices;
import cn.hamm.spms.module.wms.output.OutputEntity;
import cn.hamm.spms.module.wms.output.detail.OutputDetailEntity;
import cn.hamm.spms.module.wms.output.enums.OutputStatus;
import cn.hamm.spms.module.wms.output.enums.OutputType;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

import static cn.hamm.spms.base.config.ConfigFlag.SALE_BILL_AUTO_AUDIT;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

/**
 * <h1>Service</h1>
 *
 * @author Hamm.cn
 */
@Service
public class SaleService extends AbstractBaseBillService<SaleEntity, SaleRepository, SaleDetailEntity, SaleDetailService, SaleDetailRepository> {
    @Autowired
    private DataPermissionSpecification dataPermissionSpecification;

    @Autowired(required = false)
    private SaleMapper saleMapper;

    @Autowired
    private SaleReadShadowService saleReadShadowService;

    @Autowired
    private ExportHelper exportHelper;

    private static final int DEFAULT_READ_PAGE_SIZE = 20;
    private static final int MAX_READ_PAGE_SIZE = 200;

    /**
     * @return 是否已启用销售单 Mapper 读路径
     */
    public boolean isMapperReadEnabled() {
        return saleMapper != null;
    }

    /**
     * 销售单分页读出口；写和明细保存仍走原 JPA 单据服务。
     */
    public @NotNull QueryPageResponse<SaleEntity> getMapperPage(QueryPageRequest<SaleEntity> request) {
        QueryPageRequest<SaleEntity> normalized = request == null ? new QueryPageRequest<>() : request;
        if (!isMapperReadEnabled()) {
            return getPage(normalized);
        }
        SaleEntity filter = normalized.getFilter() == null ? new SaleEntity() : normalized.getFilter();
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
        saleReadShadowService.submit(filter);
        List<SaleEntity> rows = saleMapper.selectPageByPermission(filter.getBillCode(), filter.getReason(),
                filter.getStatus(), (int) offset, pageSize);
        long total = saleMapper.countByPermission(filter.getBillCode(), filter.getReason(), filter.getStatus());
        int totalAsInt = Math.toIntExact(total);
        int pageCount = total == 0 ? 0 : Math.toIntExact((total + pageSize - 1L) / pageSize);
        Sort sort = normalized.getSort() == null ? new Sort() : normalized.getSort();
        QueryPageResponse<SaleEntity> response = new QueryPageResponse<>();
        response.setList(rows);
        response.setTotal(totalAsInt);
        response.setPageCount(pageCount);
        response.setPage(new Page().setPageNum(pageNum).setPageSize(pageSize));
        response.setSort(sort);
        return response;
    }

    /**
     * 销售单详情读出口；加载明细后补齐客户关联。
     */
    public SaleEntity getMapperDetail(long id) {
        if (!isMapperReadEnabled()) {
            return get(id);
        }
        saleReadShadowService.submit(new SaleEntity());
        SaleEntity bill = saleMapper.selectDetailByPermission(id);
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
     * 销售单总数读出口；与分页使用同一筛选和权限语句。
     *
     * @param filter 业务筛选（单号/说明/状态）
     * @return 权限范围内总数
     */
    public long getMapperCount(SaleEntity filter) {
        SaleEntity normalized = filter == null ? new SaleEntity() : filter;
        if (isMapperReadEnabled()) {
            saleReadShadowService.submit(normalized);
            return saleMapper.countByPermission(normalized.getBillCode(), normalized.getReason(), normalized.getStatus());
        }
        return filter(normalized).size();
    }

    /**
     * 销售单统计读出口；只返回行数与已发布行数，与总数同一权限语句。
     *
     * @param filter 业务筛选（单号/说明/状态）
     * @return 权限范围内统计
     */
    public ReadStat getMapperStat(SaleEntity filter) {
        SaleEntity normalized = filter == null ? new SaleEntity() : filter;
        if (isMapperReadEnabled()) {
            saleReadShadowService.submit(normalized);
            return saleMapper.statByPermission(normalized.getBillCode(), normalized.getReason(), normalized.getStatus());
        }
        List<SaleEntity> rows = filter(normalized);
        ReadStat stat = new ReadStat();
        stat.setTotal(rows.size());
        stat.setPublishedTotal(rows.stream().filter(row -> Boolean.TRUE.equals(row.getIsPublished())).count());
        return stat;
    }

    /**
     * 销售单真实导出出口（Mapper）。
     *
     * <p>行集经 {@link SaleMapper#selectForExport} 走与分页/详情/总数相同的权限拦截链，列级遮蔽在
     * <b>请求线程</b>完成后交给 {@link ExportHelper#createExportTask} 落盘，保留异步下载契约。
     * 销售单无 {@code @SensitiveField}，{@link ExportColumnMasker#maskForExport} 不改变行内容，
     * 但保留同一份遮蔽实现以防后续新增敏感列。</p>
     *
     * @param request 导出请求（含单号/说明/状态筛选）
     * @return 导出任务文件编码，供前端 {@code queryExport} 轮询下载
     */
    public String createMapperExportTask(QueryListRequest<SaleEntity> request) {
        SaleEntity filter = request == null || request.getFilter() == null ? new SaleEntity() : request.getFilter();
        saleReadShadowService.submit(filter);
        List<SaleEntity> rows = saleMapper.selectForExport(filter.getBillCode(), filter.getReason(), filter.getStatus());
        List<SaleEntity> masked = ExportColumnMasker.maskForExport(rows);
        return exportHelper.createExportTask(() -> {
            InputStream stream = CollectionUtil.toCsvInputStream(CsvFormulaSanitizer.sanitize(masked), SaleEntity.class);
            return exportHelper.saveExportFileStream(stream);
        });
    }

    /**
     * 详情的客户关联不通过销售 SQL JOIN；沿客户服务自己的边界读取。
     */
    private void hydrateRelations(SaleEntity bill) {
        if (bill.getCustomer() != null && bill.getCustomer().getId() != null) {
            bill.setCustomer(ChannelServices.getCustomerService().get(bill.getCustomer().getId()));
        }
    }

    @Override
    protected void addSearchPredicate(@NotNull QueryWrapper<SaleEntity> wrapper, @NotNull SaleEntity search) {
        // 数据权限：渲染器产出的 SQL 片段直接挂到 wrapper 上（J2 起不再编 JPA Criteria）。
        // 引擎把这个钩子接在通用条件之后，所以片段与用户条件天然是 AND 关系。
        dataPermissionSpecification.applyQuery(wrapper, getEntityClass(), "channel.sale", DataPermissionAction.QUERY);
    }

    @Override
    public IDictionary getRejectedStatus() {
        return SaleStatus.REJECTED;
    }

    @Override
    public IDictionary getAuditedStatus() {
        return SaleStatus.OUTPUTTING;
    }

    @Override
    public IDictionary getAuditingStatus() {
        return SaleStatus.AUDITING;
    }

    @Override
    public IDictionary getBillDetailsFinishStatus() {
        return SaleStatus.DONE;
    }

    @Override
    protected void afterDetailSaved(long billId) {
        // 计算总金额
        List<SaleDetailEntity> details = detailService.getAllByBillId(billId);
        double totalPrice = details.stream()
                .mapToDouble(detail -> NumberUtil.multiply(
                        detail.getQuantity(), detail.getPrice())
                )
                .sum();
        updateToDatabase(getEntityInstance(billId).setTotalPrice(totalPrice));
    }

    @Override
    protected ConfigFlag getAutoAuditConfigFlag() {
        return SALE_BILL_AUTO_AUDIT;
    }


    @Override
    protected void afterBillAudited(long billId) {
        SaleEntity sale = get(billId);
        // 销售单审核完毕后创建出库单
        OutputEntity outputBill = new OutputEntity()
                .setStatus(OutputStatus.AUDITING.getKey())
                .setSale(sale)
                .setType(OutputType.SALE.getKey());
        List<SaleDetailEntity> details = detailService.getAllByBillId(sale.getId());
        List<OutputDetailEntity> outputDetails = details.stream()
                .map(detail -> new OutputDetailEntity()
                        .setMaterial(detail.getMaterial())
                        .setQuantity(detail.getQuantity()))
                .collect(Collectors.toList());
        outputBill.setDetails(outputDetails);
        WmsServices.getOutputService().add(outputBill);
    }
}
