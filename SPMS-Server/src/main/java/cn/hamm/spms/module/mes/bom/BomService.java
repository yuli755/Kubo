package cn.hamm.spms.module.mes.bom;

import cn.hamm.airpower.core.CollectionUtil;
import cn.hamm.airpower.curd.helper.ExportHelper;
import cn.hamm.airpower.curd.model.query.Page;
import cn.hamm.airpower.curd.model.query.QueryListRequest;
import cn.hamm.airpower.curd.model.query.QueryPageRequest;
import cn.hamm.airpower.curd.model.query.QueryPageResponse;
import cn.hamm.airpower.curd.model.query.Sort;
import cn.hamm.spms.base.BaseService;
import cn.hamm.spms.base.mapper.ReadStat;
import cn.hamm.spms.module.mes.bom.mapper.BomMapper;
import cn.hamm.spms.module.mes.bom.mapper.BomReadShadowService;
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
public class BomService extends BaseService<BomEntity, BomRepository> {
    private static final int DEFAULT_READ_PAGE_SIZE = 20;
    private static final int MAX_READ_PAGE_SIZE = 200;

    /**
     * 读侧开关关闭时 Mapper Bean 根本不存在；这里必须是可选注入，才能保留 JPA 回滚路径。
     */
    @Autowired(required = false)
    private BomMapper bomMapper;

    @Autowired
    private BomReadShadowService bomReadShadowService;

    @Autowired
    private ExportHelper exportHelper;

    @Autowired
    private DataPermissionSpecification dataPermissionSpecification;

    /**
     * @return 是否已启用 BOM Mapper 读路径
     */
    public boolean isMapperReadEnabled() {
        return bomMapper != null;
    }

    /**
     * BOM 分页读出口；写路径仍走 AirPower JPA。
     */
    public @NotNull QueryPageResponse<BomEntity> getMapperPage(QueryPageRequest<BomEntity> request) {
        QueryPageRequest<BomEntity> normalized = request == null ? new QueryPageRequest<>() : request;
        if (!isMapperReadEnabled()) {
            return getPage(normalized);
        }
        BomEntity filter = normalized.getFilter() == null ? new BomEntity() : normalized.getFilter();
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
        bomReadShadowService.submit(filter);
        List<BomEntity> list = bomMapper.selectPageByPermission(filter, offset, pageSize);
        long total = bomMapper.countByPermission(filter);
        int totalAsInt = Math.toIntExact(total);
        int pageCount = total == 0 ? 0 : Math.toIntExact((total + pageSize - 1L) / pageSize);
        Sort sort = normalized.getSort() == null ? new Sort() : normalized.getSort();
        QueryPageResponse<BomEntity> response = new QueryPageResponse<>();
        response.setList(list);
        response.setTotal(totalAsInt);
        response.setPageCount(pageCount);
        response.setPage(new Page().setPageNum(pageNum).setPageSize(pageSize));
        response.setSort(sort);
        return response;
    }

    /**
     * BOM 详情读出口；无权行由 Mapper 返回 null。
     *
     * <p>明细是 {@code @ManyToMany}（无 {@code bom_id} 外键），主行通过权限链后沿 JPA 关系补齐，
     * 避免为一张中间表手写 JOIN 读语句。</p>
     *
     * @param id 主键
     * @return 详情或 null
     */
    public BomEntity getMapperDetail(long id) {
        if (!isMapperReadEnabled()) {
            return get(id);
        }
        bomReadShadowService.submit();
        if (bomMapper.selectDetailByPermission(id) == null) {
            return null;
        }
        return get(id);
    }

    /**
     * BOM 总数读出口。
     *
     * @param filter 业务筛选
     * @return 权限范围内行数
     */
    public long getMapperCount(BomEntity filter) {
        BomEntity normalized = filter == null ? new BomEntity() : filter;
        if (isMapperReadEnabled()) {
            bomReadShadowService.submit(normalized);
            return bomMapper.countByPermission(normalized);
        }
        return filter(normalized).size();
    }

    /**
     * BOM 统计读出口。
     *
     * @param filter 业务筛选
     * @return 权限范围内统计
     */
    public ReadStat getMapperStat(BomEntity filter) {
        BomEntity normalized = filter == null ? new BomEntity() : filter;
        if (isMapperReadEnabled()) {
            bomReadShadowService.submit(normalized);
            return bomMapper.statByPermission(normalized);
        }
        List<BomEntity> rows = filter(normalized);
        ReadStat stat = new ReadStat();
        stat.setTotal(rows.size());
        stat.setPublishedTotal(rows.stream().filter(row -> Boolean.TRUE.equals(row.getIsPublished())).count());
        return stat;
    }

    /**
     * BOM 真实导出出口（Mapper）。
     *
     * <p>行集经 {@link BomMapper#selectForExport} 走同一条权限拦截链，列级遮蔽在请求线程完成后交给
     * {@link ExportHelper#createExportTask} 落盘，保留异步下载契约。</p>
     *
     * @param request 导出请求（含业务筛选）
     * @return 导出任务文件编码，供前端 {@code queryExport} 轮询下载
     */
    public String createMapperExportTask(QueryListRequest<BomEntity> request) {
        BomEntity filter = request == null || request.getFilter() == null
                ? new BomEntity() : request.getFilter();
        bomReadShadowService.submit(filter);
        List<BomEntity> rows = bomMapper.selectForExport(filter);
        List<BomEntity> masked = ExportColumnMasker.maskForExport(rows);
        return exportHelper.createExportTask(() -> {
            InputStream stream = CollectionUtil.toCsvInputStream(masked, BomEntity.class);
            return exportHelper.saveExportFileStream(stream);
        });
    }

    @Override
    protected @NotNull List<Predicate> addSearchPredicate(
            @NotNull Root<BomEntity> root,
            @NotNull CriteriaBuilder builder,
            @NotNull BomEntity search
    ) {
        List<Predicate> predicateList = new ArrayList<>();
        List<Predicate> dataPermissionPredicates = dataPermissionSpecification.buildPredicates(
                root, builder, "mes.bom", DataPermissionAction.QUERY
        );
        predicateList.addAll(dataPermissionPredicates);
        return predicateList;
    }
}
