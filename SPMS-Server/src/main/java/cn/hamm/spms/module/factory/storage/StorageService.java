package cn.hamm.spms.module.factory.storage;

import cn.hamm.spms.core.CollectionUtil;
import cn.hamm.spms.core.TreeUtil;
import cn.hamm.spms.framework.curd.helper.ExportHelper;
import cn.hamm.spms.framework.curd.model.query.Page;
import cn.hamm.spms.framework.curd.model.query.QueryListRequest;
import cn.hamm.spms.framework.curd.model.query.QueryPageRequest;
import cn.hamm.spms.framework.curd.model.query.QueryPageResponse;
import cn.hamm.spms.framework.curd.model.query.Sort;
import cn.hamm.spms.base.BaseService;
import cn.hamm.spms.base.mapper.ReadStat;
import cn.hamm.spms.common.CsvFormulaSanitizer;
import cn.hamm.spms.module.factory.storage.mapper.StorageMapper;
import cn.hamm.spms.module.factory.storage.mapper.StorageReadShadowService;
import cn.hamm.spms.framework.curd.datapermission.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionSpecification;
import cn.hamm.spms.module.system.datapermission.service.ExportColumnMasker;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

/**
 * <h1>Service</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
@Service
public class StorageService extends BaseService<StorageEntity, StorageRepository> {
    private static final int DEFAULT_READ_PAGE_SIZE = 20;
    private static final int MAX_READ_PAGE_SIZE = 200;

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
        storageReadShadowService.submit(filter);
        List<StorageEntity> list = storageMapper.selectPageByPermission(filter, offset, pageSize);
        long total = storageMapper.countByPermission(filter);
        int totalAsInt = Math.toIntExact(total);
        int pageCount = total == 0 ? 0 : Math.toIntExact((total + pageSize - 1L) / pageSize);
        Sort sort = normalized.getSort() == null ? new Sort() : normalized.getSort();
        QueryPageResponse<StorageEntity> response = new QueryPageResponse<>();
        response.setList(list);
        response.setTotal(totalAsInt);
        response.setPageCount(pageCount);
        response.setPage(new Page().setPageNum(pageNum).setPageSize(pageSize));
        response.setSort(sort);
        return response;
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
            InputStream stream = CollectionUtil.toCsvInputStream(CsvFormulaSanitizer.sanitize(masked), StorageEntity.class);
            return exportHelper.saveExportFileStream(stream);
        });
    }

    @Override
    protected void addSearchPredicate(@NotNull QueryWrapper<StorageEntity> wrapper, @NotNull StorageEntity search) {
        // 数据权限：渲染器产出的 SQL 片段直接挂到 wrapper 上（J2 起不再编 JPA Criteria）。
        // 引擎把这个钩子接在通用条件之后，所以片段与用户条件天然是 AND 关系。
        dataPermissionSpecification.applyQuery(wrapper, getEntityClass(), "factory.storage", DataPermissionAction.QUERY);
    }

    @Override
    protected @NotNull List<StorageEntity> afterGetList(@NotNull List<StorageEntity> list) {
        list.forEach(item -> {
            QueryListRequest<StorageEntity> queryListRequest = new QueryListRequest<>();
            queryListRequest.setFilter(new StorageEntity().setParentId(item.getId()));
            item.setChildren(getList(queryListRequest));
        });
        return list;
    }

    @Override
    protected void beforeDelete(@NotNull StorageEntity storage) {
        TreeUtil.ensureNoChildrenBeforeDelete(storage.getId(), id -> filter(new StorageEntity().setParentId(id)));
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
            filter.setParentId(TreeUtil.ROOT_ID);
        }
        queryListRequest.setFilter(filter);
        return queryListRequest;
    }
}
