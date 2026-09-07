package cn.hamm.spms.module.asset.material;

import cn.hamm.airpower.curd.model.query.Page;
import cn.hamm.airpower.curd.model.query.QueryPageRequest;
import cn.hamm.airpower.curd.model.query.Sort;
import cn.hamm.airpower.curd.model.query.QueryPageResponse;
import cn.hamm.spms.base.BaseService;
import cn.hamm.spms.module.asset.material.mapper.MaterialMapper;
import cn.hamm.spms.module.asset.material.mapper.MaterialReadShadowService;
import cn.hamm.spms.module.asset.material.mapper.MaterialReadStat;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionSpecification;
import cn.hamm.spms.module.system.unit.UnitService;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * <h1>Service</h1>
 *
 * @author Hamm.cn
 */
@Service
public class MaterialService extends BaseService<MaterialEntity, MaterialRepository> {
    private static final int DEFAULT_READ_PAGE_SIZE = 20;
    private static final int MAX_READ_PAGE_SIZE = 200;

    /**
     * 读侧开关关闭时 Mapper Bean 根本不存在；这里必须是可选注入，才能保留 JPA 回滚路径。
     */
    @Autowired(required = false)
    private MaterialMapper materialMapper;

    @Autowired
    private MaterialReadShadowService materialReadShadowService;

    /**
     * @return 是否已启用 Material 的 Mapper 读路径
     */
    public boolean isMapperReadEnabled() {
        return materialMapper != null;
    }

    @Autowired
    private DataPermissionSpecification dataPermissionSpecification;

    /**
     * 物料读侧分页：列表数据与总数使用相同的业务筛选，行级权限由 Mapper 拦截器统一追加。
     *
     * @param request 分页请求
     * @return 分页结果
     */
    public @NotNull QueryPageResponse<MaterialEntity> getMapperPage(QueryPageRequest<MaterialEntity> request) {
        QueryPageRequest<MaterialEntity> normalized = request == null ? new QueryPageRequest<>() : request;
        if (!isMapperReadEnabled()) {
            return getPage(normalized);
        }
        MaterialEntity filter = normalized.getFilter() == null ? new MaterialEntity() : normalized.getFilter();
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
        materialReadShadowService.submit();
        List<MaterialEntity> list = materialMapper.selectPageByPermission(filter, offset, pageSize);
        long total = materialMapper.countByPermission(filter);
        int totalAsInt = Math.toIntExact(total);
        int pageCount = total == 0 ? 0 : Math.toIntExact((total + pageSize - 1L) / pageSize);
        Sort sort = normalized.getSort() == null ? new Sort() : normalized.getSort();
        QueryPageResponse<MaterialEntity> response = new QueryPageResponse<>();
        response.setList(list);
        response.setTotal(totalAsInt);
        response.setPageCount(pageCount);
        response.setPage(new Page().setPageNum(pageNum).setPageSize(pageSize));
        response.setSort(sort);
        return response;
    }

    /**
     * 物料详情读侧出口；无权行与不存在行都由 Mapper 返回 null。
     *
     * @param id 主键
     * @return 详情或 null
     */
    public MaterialEntity getMapperDetail(long id) {
        if (!isMapperReadEnabled()) {
            return get(id);
        }
        materialReadShadowService.submit();
        return materialMapper.selectDetailByPermission(id);
    }

    /**
     * 物料总数读侧出口。
     *
     * @param filter 业务筛选
     * @return 权限范围内行数
     */
    public long getMapperCount(MaterialEntity filter) {
        MaterialEntity normalized = filter == null ? new MaterialEntity() : filter;
        if (isMapperReadEnabled()) {
            materialReadShadowService.submit();
            return materialMapper.countByPermission(normalized);
        }
        return filter(normalized).size();
    }

    /**
     * 物料统计读侧出口。
     *
     * @param filter 业务筛选
     * @return 权限范围内统计
     */
    public MaterialReadStat getMapperStat(MaterialEntity filter) {
        MaterialEntity normalized = filter == null ? new MaterialEntity() : filter;
        if (isMapperReadEnabled()) {
            materialReadShadowService.submit();
            return materialMapper.statByPermission(normalized);
        }
        List<MaterialEntity> rows = filter(normalized);
        MaterialReadStat stat = new MaterialReadStat();
        stat.setTotal(rows.size());
        stat.setPublishedTotal(rows.stream().filter(row -> Boolean.TRUE.equals(row.getIsPublished())).count());
        return stat;
    }

    @Override
    protected @NotNull List<Predicate> addSearchPredicate(
            @NotNull Root<MaterialEntity> root,
            @NotNull CriteriaBuilder builder,
            @NotNull MaterialEntity search
    ) {
        List<Predicate> predicateList = new ArrayList<>();
        List<Predicate> dataPermissionPredicates = dataPermissionSpecification.buildPredicates(
                root, builder, "asset.material", DataPermissionAction.QUERY
        );
        predicateList.addAll(dataPermissionPredicates);
        return predicateList;
    }

    @Autowired
    private UnitService unitService;

    @Override
    protected MaterialEntity beforeAppSaveToDatabase(@NotNull MaterialEntity material) {
        material.setPurchasePrice(Objects.requireNonNullElse(material.getPurchasePrice(), 0D));
        material.setSalePrice(Objects.requireNonNullElse(material.getSalePrice(), 0D));
        return material;
    }
}
