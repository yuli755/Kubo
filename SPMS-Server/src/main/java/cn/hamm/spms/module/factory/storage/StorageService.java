package cn.hamm.spms.module.factory.storage;

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
import cn.hamm.spms.module.factory.storage.mapper.StorageMapper;
import cn.hamm.spms.module.factory.storage.mapper.StorageReadShadowService;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionSpecification;
import cn.hamm.spms.module.system.datapermission.service.ExportColumnMasker;
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
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * <h1>Service</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
@Service
public class StorageService extends BaseService<StorageEntity, StorageRepository> {

    /**
     * 读侧开关关闭时 Mapper Bean 根本不存在；这里必须是可选注入，才能保留 JPA 回滚路径。
     */
    @Autowired(required = false)
    private StorageMapper storageMapper;

    @Autowired
    private StorageReadShadowService storageReadShadowService;

    @Autowired
    private ExportHelper exportHelper;

    @Autowired
    private DataPermissionSpecification dataPermissionSpecification;

    @Autowired
    private StorageHierarchyIndex hierarchyIndex;

    @Autowired
    private cn.hamm.spms.module.system.datapermission.event.DataPermissionEventPublisher eventPublisher;

    /**
     * @return 是否已启用 Storage 的 Mapper 读路径
     */
    public boolean isMapperReadEnabled() {
        return storageMapper != null;
    }

    /**
     * 仓库分页读出口；树导航（children）仍走 JPA {@code getList}，写路径仍走 AirPower JPA。
     *
     * @param request 分页请求
     * @return 分页结果
     */
    public @NotNull QueryPageResponse<StorageEntity> getMapperPage(QueryPageRequest<StorageEntity> request) {
        QueryPageRequest<StorageEntity> normalized = request == null ? new QueryPageRequest<>() : request;
        if (!isMapperReadEnabled()) {
            return getPage(normalized);
        }
        StorageEntity filter = normalized.getFilter() == null ? new StorageEntity() : normalized.getFilter();
        ReadPage paging = mapperReadPage(normalized.getPage());
        storageReadShadowService.submit(filter);
        List<StorageEntity> list = storageMapper.selectPageByPermission(filter, paging.offset(), paging.pageSize());
        long total = storageMapper.countByPermission(filter);
        return mapperPageResponse(paging, list, total, normalized.getSort());
    }

    /**
     * 仓库详情读出口；无权行由 Mapper 返回 null。
     *
     * @param id 主键
     * @return 详情或 null
     */
    public StorageEntity getMapperDetail(long id) {
        if (!isMapperReadEnabled()) {
            return get(id);
        }
        storageReadShadowService.submit();
        if (storageMapper.selectDetailByPermission(id) == null) {
            return null;
        }
        return get(id);
    }

    /**
     * 仓库总数读出口。
     *
     * @param filter 业务筛选
     * @return 权限范围内行数
     */
    public long getMapperCount(StorageEntity filter) {
        StorageEntity normalized = filter == null ? new StorageEntity() : filter;
        if (isMapperReadEnabled()) {
            storageReadShadowService.submit(normalized);
            return storageMapper.countByPermission(normalized);
        }
        return filter(normalized).size();
    }

    /**
     * 仓库统计读出口。
     *
     * @param filter 业务筛选
     * @return 权限范围内统计
     */
    public ReadStat getMapperStat(StorageEntity filter) {
        StorageEntity normalized = filter == null ? new StorageEntity() : filter;
        if (isMapperReadEnabled()) {
            storageReadShadowService.submit(normalized);
            return storageMapper.statByPermission(normalized);
        }
        List<StorageEntity> rows = filter(normalized);
        ReadStat stat = new ReadStat();
        stat.setTotal(rows.size());
        stat.setPublishedTotal(rows.stream().filter(row -> Boolean.TRUE.equals(row.getIsPublished())).count());
        return stat;
    }

    /**
     * 仓库真实导出出口（Mapper）。
     *
     * <p>行集经 {@link StorageMapper#selectForExport} 走同一条权限拦截链，列级遮蔽在请求线程完成后交给
     * {@link ExportHelper#createExportTask} 落盘，保留异步下载契约。</p>
     *
     * @param request 导出请求（含业务筛选）
     * @return 导出任务文件编码，供前端 {@code queryExport} 轮询下载
     */
    public String createMapperExportTask(QueryListRequest<StorageEntity> request) {
        StorageEntity filter = request == null || request.getFilter() == null
                ? new StorageEntity() : request.getFilter();
        storageReadShadowService.submit(filter);
        List<StorageEntity> rows = storageMapper.selectForExport(filter);
        List<StorageEntity> masked = ExportColumnMasker.maskForExport(rows);
        return exportHelper.createExportTask(() -> {
            InputStream stream = EntityCsvExport.toCsvInputStream(CsvFormulaSanitizer.sanitize(masked), StorageEntity.class);
            return exportHelper.saveExportFileStream(stream);
        });
    }

    @Override
    protected @NotNull List<Predicate> addSearchPredicate(
            @NotNull Root<StorageEntity> root,
            @NotNull CriteriaBuilder builder,
            @NotNull StorageEntity search
    ) {
        List<Predicate> predicateList = new ArrayList<>();
        List<Predicate> dataPermissionPredicates = dataPermissionSpecification.buildPredicates(
                root, builder, "factory.storage", DataPermissionAction.QUERY
        );
        predicateList.addAll(dataPermissionPredicates);
        return predicateList;
    }

    @Override
    protected @NotNull List<StorageEntity> afterGetList(@NotNull List<StorageEntity> list) {
        attachChildren(list);
        return list;
    }

    /**
     * 逐层为节点挂子级。
     *
     * <p>把原来「对每个节点递归调一次 {@code getList}」的 O(N) 查询，改成
     * 「每层一次 {@code parentId IN (...)} 批量查」—— 深度 D 的树只需 D+1 次查询。
     * 递归深度 = 树深（与旧实现相同），但查询次数从随节点数线性增长降为随深度增长。</p>
     */
    private void attachChildren(@NotNull List<StorageEntity> nodes) {
        if (nodes.isEmpty()) {
            return;
        }
        List<Long> parentIds = nodes.stream()
                .map(StorageEntity::getId)
                .filter(Objects::nonNull)
                .toList();
        if (parentIds.isEmpty()) {
            return;
        }
        List<StorageEntity> children = queryChildrenByParentIds(parentIds);
        if (children.isEmpty()) {
            return;
        }
        Map<Long, List<StorageEntity>> byParent = children.stream()
                .collect(Collectors.groupingBy(StorageEntity::getParentId));
        for (StorageEntity node : nodes) {
            List<StorageEntity> sub = byParent.getOrDefault(node.getId(), List.of());
            node.setChildren(sub);
            attachChildren(sub);
        }
    }

    /**
     * 一次取出 {@code parentId IN (parentIds)} 的全部子级，并**保留数据权限谓词**。
     *
     * <p>不能改用无谓词的 {@code findAllByParentIdIn}：那会绕过 {@code addSearchPredicate}
     * 注入的 {@code factory.storage} QUERY 策略，造成越权读取。</p>
     */
    private @NotNull List<StorageEntity> queryChildrenByParentIds(@NotNull List<Long> parentIds) {
        return repository.findAll((root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = addSearchPredicate(root, criteriaBuilder, new StorageEntity());
            predicates.add(root.get("parentId").in(parentIds));
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        });
    }

    @Override
    protected void beforeDelete(@NotNull StorageEntity storage) {
        TreeDeletionGuard.ensureNoChildrenBeforeDelete(storage.getId(), id -> filter(new StorageEntity().setParentId(id)));
        afterTreeChanged();
    }

    @Override
    protected void afterAppAdd(long id, @NotNull StorageEntity storage) {
        super.afterAppAdd(id, storage);
        afterTreeChanged();
    }

    @Override
    protected void afterUpdate(long id, @NotNull StorageEntity storage) {
        super.afterUpdate(id, storage);
        afterTreeChanged();
    }

    /**
     * 仓库/工厂树变了：打掉本进程的层级快照，并广播给其他节点（D13）
     *
     * <p>与 {@code DepartmentService#afterTreeChanged} 同构；Redis 不可用时广播只记 WARN，
     * 各节点靠 TTL（默认 300 秒）自愈。</p>
     */
    private void afterTreeChanged() {
        hierarchyIndex.invalidate();
        try {
            eventPublisher.publishStorageTreeChanged();
        } catch (RuntimeException e) {
            log.warn("仓库/工厂树变更广播失败（各节点将在 TTL 后自愈）: {}", e.getMessage());
        }
    }

    @Override
    protected @NotNull QueryListRequest<StorageEntity> beforeGetList(@NotNull QueryListRequest<StorageEntity> queryListRequest) {
        StorageEntity filter = queryListRequest.getFilter();
        if (Objects.isNull(filter.getParentId())) {
            filter.setParentId(Trees.ROOT_ID);
        }
        queryListRequest.setFilter(filter);
        return queryListRequest;
    }
}
