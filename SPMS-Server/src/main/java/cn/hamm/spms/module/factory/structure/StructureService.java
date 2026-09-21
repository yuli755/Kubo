package cn.hamm.spms.module.factory.structure;

import cn.hamm.spms.common.tree.TreeDeletionGuard;
import cn.hamm.spms.framework.util.Trees;
import cn.hamm.spms.common.export.EntityCsvExport;
import cn.hamm.spms.common.export.ExportHelper;
import cn.hamm.spms.framework.query.QueryListRequest;
import cn.hamm.spms.framework.query.QueryPageRequest;
import cn.hamm.spms.framework.query.QueryPageResponse;
import cn.hamm.spms.framework.query.ReadPage;
import cn.hamm.spms.base.BaseService;
import cn.hamm.spms.base.mapper.ReadStat;
import cn.hamm.spms.common.CsvFormulaSanitizer;
import cn.hamm.spms.module.factory.structure.mapper.StructureMapper;
import cn.hamm.spms.module.factory.structure.mapper.StructureReadShadowService;
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
import java.util.Objects;

/**
 * <h1>Service</h1>
 *
 * @author Hamm.cn
 */
@Service
public class StructureService extends BaseService<StructureEntity, StructureRepository> {

    /**
     * 读侧开关关闭时 Mapper Bean 根本不存在；这里必须是可选注入，才能保留 JPA 回滚路径。
     */
    @Autowired(required = false)
    private StructureMapper structureMapper;

    @Autowired
    private StructureReadShadowService structureReadShadowService;

    @Autowired
    private ExportHelper exportHelper;

    @Autowired
    private DataPermissionSpecification dataPermissionSpecification;

    /**
     * @return 是否已启用 Structure 的 Mapper 读路径
     */
    public boolean isMapperReadEnabled() {
        return structureMapper != null;
    }

    /**
     * 生产单元分页读出口；树导航（children）仍走 JPA {@code getList}，写路径仍走 AirPower JPA。
     *
     * @param request 分页请求
     * @return 分页结果
     */
    public @NotNull QueryPageResponse<StructureEntity> getMapperPage(QueryPageRequest<StructureEntity> request) {
        QueryPageRequest<StructureEntity> normalized = request == null ? new QueryPageRequest<>() : request;
        if (!isMapperReadEnabled()) {
            return getPage(normalized);
        }
        StructureEntity filter = normalized.getFilter() == null ? new StructureEntity() : normalized.getFilter();
        ReadPage paging = mapperReadPage(normalized.getPage());
        structureReadShadowService.submit(filter);
        List<StructureEntity> list = structureMapper.selectPageByPermission(filter, paging.offset(), paging.pageSize());
        long total = structureMapper.countByPermission(filter);
        return mapperPageResponse(paging, list, total, normalized.getSort());
    }

    /**
     * 生产单元详情读出口；无权行由 Mapper 返回 null。
     *
     * <p>工序列表是 {@code @ManyToMany}（无外键），主行通过权限链后沿 JPA 关系补齐。</p>
     *
     * @param id 主键
     * @return 详情或 null
     */
    public StructureEntity getMapperDetail(long id) {
        if (!isMapperReadEnabled()) {
            return get(id);
        }
        structureReadShadowService.submit();
        if (structureMapper.selectDetailByPermission(id) == null) {
            return null;
        }
        return get(id);
    }

    /**
     * 生产单元总数读出口。
     *
     * @param filter 业务筛选
     * @return 权限范围内行数
     */
    public long getMapperCount(StructureEntity filter) {
        StructureEntity normalized = filter == null ? new StructureEntity() : filter;
        if (isMapperReadEnabled()) {
            structureReadShadowService.submit(normalized);
            return structureMapper.countByPermission(normalized);
        }
        return filter(normalized).size();
    }

    /**
     * 生产单元统计读出口。
     *
     * @param filter 业务筛选
     * @return 权限范围内统计
     */
    public ReadStat getMapperStat(StructureEntity filter) {
        StructureEntity normalized = filter == null ? new StructureEntity() : filter;
        if (isMapperReadEnabled()) {
            structureReadShadowService.submit(normalized);
            return structureMapper.statByPermission(normalized);
        }
        List<StructureEntity> rows = filter(normalized);
        ReadStat stat = new ReadStat();
        stat.setTotal(rows.size());
        stat.setPublishedTotal(rows.stream().filter(row -> Boolean.TRUE.equals(row.getIsPublished())).count());
        return stat;
    }

    /**
     * 生产单元真实导出出口（Mapper）。
     *
     * <p>行集经 {@link StructureMapper#selectForExport} 走同一条权限拦截链，列级遮蔽在请求线程完成后交给
     * {@link ExportHelper#createExportTask} 落盘，保留异步下载契约。</p>
     *
     * @param request 导出请求（含业务筛选）
     * @return 导出任务文件编码，供前端 {@code queryExport} 轮询下载
     */
    public String createMapperExportTask(QueryListRequest<StructureEntity> request) {
        StructureEntity filter = request == null || request.getFilter() == null
                ? new StructureEntity() : request.getFilter();
        structureReadShadowService.submit(filter);
        List<StructureEntity> rows = structureMapper.selectForExport(filter);
        List<StructureEntity> masked = ExportColumnMasker.maskForExport(rows);
        return exportHelper.createExportTask(() -> {
            InputStream stream = EntityCsvExport.toCsvInputStream(CsvFormulaSanitizer.sanitize(masked), StructureEntity.class);
            return exportHelper.saveExportFileStream(stream);
        });
    }

    @Override
    protected @NotNull List<Predicate> addSearchPredicate(
            @NotNull Root<StructureEntity> root,
            @NotNull CriteriaBuilder builder,
            @NotNull StructureEntity search
    ) {
        List<Predicate> predicateList = new ArrayList<>();
        List<Predicate> dataPermissionPredicates = dataPermissionSpecification.buildPredicates(
                root, builder, "factory.structure", DataPermissionAction.QUERY
        );
        predicateList.addAll(dataPermissionPredicates);
        return predicateList;
    }

    @Override
    protected @NotNull List<StructureEntity> afterGetList(@NotNull List<StructureEntity> list) {
        list.forEach(item -> {
            QueryListRequest<StructureEntity> queryListRequest = new QueryListRequest<>();
            queryListRequest.setFilter(new StructureEntity().setParentId(item.getId()));
            item.setChildren(getList(queryListRequest));
        });
        return list;
    }

    @Override
    protected void beforeDelete(@NotNull StructureEntity structure) {
        TreeDeletionGuard.ensureNoChildrenBeforeDelete(structure.getId(), id -> filter(new StructureEntity().setParentId(id)));
    }

    @Override
    protected @NotNull QueryListRequest<StructureEntity> beforeGetList(@NotNull QueryListRequest<StructureEntity> queryListRequest) {
        StructureEntity filter = queryListRequest.getFilter();
        if (Objects.isNull(filter.getParentId())) {
            filter.setParentId(Trees.ROOT_ID);
        }
        queryListRequest.setFilter(filter);
        return queryListRequest;
    }
}
