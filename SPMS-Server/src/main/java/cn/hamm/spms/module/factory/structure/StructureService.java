package cn.hamm.spms.module.factory.structure;

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
import cn.hamm.spms.module.factory.structure.mapper.StructureMapper;
import cn.hamm.spms.framework.curd.datapermission.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionSpecification;
import cn.hamm.spms.module.system.datapermission.service.ExportColumnMasker;
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
@Service
public class StructureService extends BaseService<StructureEntity, StructureRepository> {
    private static final int DEFAULT_READ_PAGE_SIZE = 20;
    private static final int MAX_READ_PAGE_SIZE = 200;

    /**
     * 读侧开关关闭时 Mapper Bean 根本不存在；这里必须是可选注入，才能保留「关掉读侧就回到引擎读路径」的回滚位。
     */
    @Autowired(required = false)
    private StructureMapper structureMapper;

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
     * 生产单元分页读出口；树导航（children）仍走 JPA {@code getList}，写路径走写侧仓储（`ICurdRepository` + mapper XML）。
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
        List<StructureEntity> list = structureMapper.selectPageByPermission(filter, offset, pageSize);
        long total = structureMapper.countByPermission(filter);
        int totalAsInt = Math.toIntExact(total);
        int pageCount = total == 0 ? 0 : Math.toIntExact((total + pageSize - 1L) / pageSize);
        Sort sort = normalized.getSort() == null ? new Sort() : normalized.getSort();
        QueryPageResponse<StructureEntity> response = new QueryPageResponse<>();
        response.setList(list);
        response.setTotal(totalAsInt);
        response.setPageCount(pageCount);
        response.setPage(new Page().setPageNum(pageNum).setPageSize(pageSize));
        response.setSort(sort);
        return response;
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
        List<StructureEntity> rows = structureMapper.selectForExport(filter);
        List<StructureEntity> masked = ExportColumnMasker.maskForExport(rows);
        return exportHelper.createExportTask(() -> {
            InputStream stream = CollectionUtil.toCsvInputStream(CsvFormulaSanitizer.sanitize(masked), StructureEntity.class);
            return exportHelper.saveExportFileStream(stream);
        });
    }

    @Override
    protected void addSearchPredicate(@NotNull QueryWrapper<StructureEntity> wrapper, @NotNull StructureEntity search) {
        // 数据权限：渲染器产出的 SQL 片段直接挂到 wrapper 上（J2 起不再编 JPA Criteria）。
        // 引擎把这个钩子接在通用条件之后，所以片段与用户条件天然是 AND 关系。
        dataPermissionSpecification.applyQuery(wrapper, getEntityClass(), "factory.structure", DataPermissionAction.QUERY);
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
        TreeUtil.ensureNoChildrenBeforeDelete(structure.getId(), id -> filter(new StructureEntity().setParentId(id)));
    }

    @Override
    protected @NotNull QueryListRequest<StructureEntity> beforeGetList(@NotNull QueryListRequest<StructureEntity> queryListRequest) {
        StructureEntity filter = queryListRequest.getFilter();
        if (Objects.isNull(filter.getParentId())) {
            filter.setParentId(TreeUtil.ROOT_ID);
        }
        queryListRequest.setFilter(filter);
        return queryListRequest;
    }
}
