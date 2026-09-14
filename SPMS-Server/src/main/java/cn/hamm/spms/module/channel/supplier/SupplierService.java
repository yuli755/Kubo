package cn.hamm.spms.module.channel.supplier;

import cn.hamm.airpower.core.CollectionUtil;
import cn.hamm.airpower.curd.helper.ExportHelper;
import cn.hamm.airpower.curd.model.query.Page;
import cn.hamm.airpower.curd.model.query.QueryListRequest;
import cn.hamm.airpower.curd.model.query.QueryPageRequest;
import cn.hamm.airpower.curd.model.query.QueryPageResponse;
import cn.hamm.airpower.curd.model.query.Sort;
import cn.hamm.spms.base.BaseService;
import cn.hamm.spms.base.mapper.ReadStat;
import cn.hamm.spms.common.CsvFormulaSanitizer;
import cn.hamm.spms.module.channel.supplier.mapper.SupplierMapper;
import cn.hamm.spms.module.channel.supplier.mapper.SupplierReadShadowService;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionSpecification;
import cn.hamm.spms.module.system.datapermission.service.ExportColumnMasker;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * <h1>Service</h1>
 *
 * @author Hamm.cn
 */
@Service
public class SupplierService extends BaseService<SupplierEntity, SupplierRepository> {
    private static final int DEFAULT_READ_PAGE_SIZE = 20;
    private static final int MAX_READ_PAGE_SIZE = 200;

    /**
     * 读侧开关关闭时 Mapper Bean 根本不存在；这里必须是可选注入，才能保留 JPA 回滚路径。
     */
    @Autowired(required = false)
    private SupplierMapper supplierMapper;

    @Autowired
    private SupplierReadShadowService supplierReadShadowService;

    @Autowired
    private ExportHelper exportHelper;

    @Autowired
    private DataPermissionSpecification dataPermissionSpecification;

    /**
     * @return 是否已启用供应商 Mapper 读路径
     */
    public boolean isMapperReadEnabled() {
        return supplierMapper != null;
    }

    /**
     * 供应商分页读出口；写路径仍走 AirPower JPA。
     */
    public @NotNull QueryPageResponse<SupplierEntity> getMapperPage(QueryPageRequest<SupplierEntity> request) {
        QueryPageRequest<SupplierEntity> normalized = request == null ? new QueryPageRequest<>() : request;
        if (!isMapperReadEnabled()) {
            return getPage(normalized);
        }
        SupplierEntity filter = normalized.getFilter() == null ? new SupplierEntity() : normalized.getFilter();
        Page requestedPage = normalized.getPage() == null ? new Page() : normalized.getPage();
        int pageNum = requestedPage.getPageNum() == null ? 1 : requestedPage.getPageNum();
        int pageSize = requestedPage.getPageSize() == null
                ? DEFAULT_READ_PAGE_SIZE : requestedPage.getPageSize();
        if (pageNum < 1 || pageSize < 1) {
            throw new IllegalArgumentException("分页页码和分页大小必须为正数");
        }
        pageSize = Math.min(pageSize, MAX_READ_PAGE_SIZE);
        long offsetValue = ((long) pageNum - 1L) * pageSize;
        if (offsetValue > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("分页页码超出可查询范围");
        }
        int offset = (int) offsetValue;
        supplierReadShadowService.submit(filter);
        List<SupplierEntity> list = supplierMapper.selectPageByPermission(filter, offset, pageSize);
        long total = supplierMapper.countByPermission(filter);
        int totalAsInt = Math.toIntExact(total);
        int pageCount = total == 0 ? 0 : Math.toIntExact((total + pageSize - 1L) / pageSize);
        Sort sort = normalized.getSort() == null ? new Sort() : normalized.getSort();
        QueryPageResponse<SupplierEntity> response = new QueryPageResponse<>();
        response.setList(list);
        response.setTotal(totalAsInt);
        response.setPageCount(pageCount);
        response.setPage(new Page().setPageNum(pageNum).setPageSize(pageSize));
        response.setSort(sort);
        return response;
    }

    /**
     * 供应商详情读出口；无权行与不存在行都由 Mapper 返回 null。
     *
     * @param id 主键
     * @return 详情或 null
     */
    public SupplierEntity getMapperDetail(long id) {
        if (!isMapperReadEnabled()) {
            return get(id);
        }
        supplierReadShadowService.submit();
        return supplierMapper.selectDetailByPermission(id);
    }

    /**
     * 供应商总数读出口。
     *
     * @param filter 业务筛选
     * @return 权限范围内行数
     */
    public long getMapperCount(SupplierEntity filter) {
        SupplierEntity normalized = filter == null ? new SupplierEntity() : filter;
        if (isMapperReadEnabled()) {
            supplierReadShadowService.submit(normalized);
            return supplierMapper.countByPermission(normalized);
        }
        return filter(normalized).size();
    }

    /**
     * 供应商统计读出口。
     *
     * @param filter 业务筛选
     * @return 权限范围内统计
     */
    public ReadStat getMapperStat(SupplierEntity filter) {
        SupplierEntity normalized = filter == null ? new SupplierEntity() : filter;
        if (isMapperReadEnabled()) {
            supplierReadShadowService.submit(normalized);
            return supplierMapper.statByPermission(normalized);
        }
        List<SupplierEntity> rows = filter(normalized);
        ReadStat stat = new ReadStat();
        stat.setTotal(rows.size());
        stat.setPublishedTotal(rows.stream().filter(row -> Boolean.TRUE.equals(row.getIsPublished())).count());
        return stat;
    }

    /**
     * 供应商真实导出出口（Mapper）。
     *
     * <p>行集经 {@link SupplierMapper#selectForExport} 走同一条权限拦截链，列级遮蔽在<b>请求线程</b>
     * 完成后交给 {@link ExportHelper#createExportTask} 落盘，保留异步下载契约。</p>
     *
     * @param request 导出请求（含业务筛选）
     * @return 导出任务文件编码，供前端 {@code queryExport} 轮询下载
     */
    public String createMapperExportTask(QueryListRequest<SupplierEntity> request) {
        SupplierEntity filter = request == null || request.getFilter() == null
                ? new SupplierEntity() : request.getFilter();
        supplierReadShadowService.submit(filter);
        List<SupplierEntity> rows = supplierMapper.selectForExport(filter);
        List<SupplierEntity> masked = ExportColumnMasker.maskForExport(rows);
        return exportHelper.createExportTask(() -> {
            InputStream stream = CollectionUtil.toCsvInputStream(CsvFormulaSanitizer.sanitize(masked), SupplierEntity.class);
            return exportHelper.saveExportFileStream(stream);
        });
    }

    @Override
    protected @NotNull List<Predicate> addSearchPredicate(
            @NotNull Root<SupplierEntity> root,
            @NotNull CriteriaBuilder builder,
            @NotNull SupplierEntity search
    ) {
        List<Predicate> predicateList = new ArrayList<>();
        List<Predicate> dataPermissionPredicates = dataPermissionSpecification.buildPredicates(
                root, builder, "channel.supplier", DataPermissionAction.QUERY
        );
        predicateList.addAll(dataPermissionPredicates);
        return predicateList;
    }
}
