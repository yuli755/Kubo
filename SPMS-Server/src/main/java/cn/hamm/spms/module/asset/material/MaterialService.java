package cn.hamm.spms.module.asset.material;

import cn.hamm.airpower.curd.model.query.Page;
import cn.hamm.airpower.curd.model.query.QueryPageRequest;
import cn.hamm.airpower.curd.model.query.Sort;
import cn.hamm.airpower.curd.model.query.QueryPageResponse;
import cn.hamm.spms.base.BaseService;
import cn.hamm.spms.module.asset.material.mapper.MaterialMapper;
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

    @Autowired
    private MaterialMapper materialMapper;

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
        List<MaterialEntity> list = materialMapper.selectPageByPermission(filter, offset, pageSize);
        long total = materialMapper.countByPermission(filter);
        int totalAsInt = Math.toIntExact(total);
        int pageCount = total == 0 ? 0 : Math.toIntExact((total + pageSize - 1L) / pageSize);
        Sort sort = normalized.getSort() == null ? new Sort() : normalized.getSort();
        return new QueryPageResponse<MaterialEntity>()
                .setList(list)
                .setTotal(totalAsInt)
                .setPageCount(pageCount)
                .setPage(new Page().setPageNum(pageNum).setPageSize(pageSize))
                .setSort(sort);
    }

    /**
     * 物料详情读侧出口；无权行与不存在行都由 Mapper 返回 null。
     *
     * @param id 主键
     * @return 详情或 null
     */
    public MaterialEntity getMapperDetail(long id) {
        return materialMapper.selectDetailByPermission(id);
    }

    /**
     * 物料总数读侧出口。
     *
     * @param filter 业务筛选
     * @return 权限范围内行数
     */
    public long getMapperCount(MaterialEntity filter) {
        return materialMapper.countByPermission(filter == null ? new MaterialEntity() : filter);
    }

    /**
     * 物料统计读侧出口。
     *
     * @param filter 业务筛选
     * @return 权限范围内统计
     */
    public MaterialReadStat getMapperStat(MaterialEntity filter) {
        return materialMapper.statByPermission(filter == null ? new MaterialEntity() : filter);
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
