package cn.hamm.spms.module.mes.routing;

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
import cn.hamm.spms.module.mes.MesServices;
import cn.hamm.spms.module.mes.routing.mapper.RoutingMapper;
import cn.hamm.spms.module.mes.routing.mapper.RoutingReadShadowService;
import cn.hamm.spms.module.mes.routing.operation.RoutingOperationEntity;
import cn.hamm.spms.module.mes.routing.operation.RoutingOperationService;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionSpecification;
import cn.hamm.spms.module.system.datapermission.service.ExportColumnMasker;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static cn.hamm.airpower.exception.Errors.FORBIDDEN_EDIT;
import static cn.hamm.airpower.exception.Errors.PARAM_INVALID;

/**
 * <h1>Service</h1>
 *
 * @author zfy
 */
@Service
public class RoutingService extends BaseService<RoutingEntity, RoutingRepository> {
    private static final int DEFAULT_READ_PAGE_SIZE = 20;
    private static final int MAX_READ_PAGE_SIZE = 200;

    @Autowired
    private DataPermissionSpecification dataPermissionSpecification;

    /**
     * 读侧开关关闭时 Mapper Bean 根本不存在；这里必须是可选注入，才能保留 JPA 回滚路径。
     */
    @Autowired(required = false)
    private RoutingMapper routingMapper;

    @Autowired
    private RoutingReadShadowService routingReadShadowService;

    @Autowired
    private ExportHelper exportHelper;

    @Override
    protected @NotNull List<Predicate> addSearchPredicate(
            @NotNull Root<RoutingEntity> root,
            @NotNull CriteriaBuilder builder,
            @NotNull RoutingEntity search
    ) {
        List<Predicate> predicateList = new ArrayList<>();
        List<Predicate> dataPermissionPredicates = dataPermissionSpecification.buildPredicates(
                root, builder, "mes.routing", DataPermissionAction.QUERY
        );
        predicateList.addAll(dataPermissionPredicates);
        return predicateList;
    }

    /**
     * 排序字段
     */
    private static final String ORDER_FIELD_NAME = "orderNo";

    /**
     * @return 是否已启用工艺 Mapper 读路径
     */
    public boolean isMapperReadEnabled() {
        return routingMapper != null;
    }

    /**
     * 工艺分页读出口；写路径仍走 AirPower JPA。
     */
    public @NotNull QueryPageResponse<RoutingEntity> getMapperPage(QueryPageRequest<RoutingEntity> request) {
        QueryPageRequest<RoutingEntity> normalized = request == null ? new QueryPageRequest<>() : request;
        if (!isMapperReadEnabled()) {
            return getPage(normalized);
        }
        RoutingEntity filter = normalized.getFilter() == null ? new RoutingEntity() : normalized.getFilter();
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
        routingReadShadowService.submit(filter);
        List<RoutingEntity> list = routingMapper.selectPageByPermission(filter, offset, pageSize);
        long total = routingMapper.countByPermission(filter);
        int totalAsInt = Math.toIntExact(total);
        int pageCount = total == 0 ? 0 : Math.toIntExact((total + pageSize - 1L) / pageSize);
        Sort sort = normalized.getSort() == null ? new Sort() : normalized.getSort();
        QueryPageResponse<RoutingEntity> response = new QueryPageResponse<>();
        response.setList(list);
        response.setTotal(totalAsInt);
        response.setPageCount(pageCount);
        response.setPage(new Page().setPageNum(pageNum).setPageSize(pageSize));
        response.setSort(sort);
        return response;
    }

    /**
     * 工艺详情读出口；无权行与不存在行都由 Mapper 返回 null。
     *
     * <p>{@code material}/{@code bom} 为 EAGER 关联，{@code details}（工序配置列表）由
     * {@link #afterAppGet} 水合；故在通过权限链后直接复用 JPA {@code get} 补齐关系，
     * 避免为一张中间表手写 JOIN 读语句。</p>
     *
     * @param id 主键
     * @return 详情或 null
     */
    public RoutingEntity getMapperDetail(long id) {
        if (!isMapperReadEnabled()) {
            return get(id);
        }
        routingReadShadowService.submit();
        if (routingMapper.selectDetailByPermission(id) == null) {
            return null;
        }
        return get(id);
    }

    /**
     * 工艺总数读出口。
     *
     * @param filter 业务筛选
     * @return 权限范围内行数
     */
    public long getMapperCount(RoutingEntity filter) {
        RoutingEntity normalized = filter == null ? new RoutingEntity() : filter;
        if (isMapperReadEnabled()) {
            routingReadShadowService.submit(normalized);
            return routingMapper.countByPermission(normalized);
        }
        return filter(normalized).size();
    }

    /**
     * 工艺统计读出口。
     *
     * @param filter 业务筛选
     * @return 权限范围内统计
     */
    public ReadStat getMapperStat(RoutingEntity filter) {
        RoutingEntity normalized = filter == null ? new RoutingEntity() : filter;
        if (isMapperReadEnabled()) {
            routingReadShadowService.submit(normalized);
            return routingMapper.statByPermission(normalized);
        }
        List<RoutingEntity> rows = filter(normalized);
        ReadStat stat = new ReadStat();
        stat.setTotal(rows.size());
        stat.setPublishedTotal(rows.stream().filter(row -> Boolean.TRUE.equals(row.getIsPublished())).count());
        return stat;
    }

    /**
     * 工艺真实导出出口（Mapper）。
     *
     * <p>行集经 {@link RoutingMapper#selectForExport} 走同一条权限拦截链，列级遮蔽在请求线程完成后交给
     * {@link ExportHelper#createExportTask} 落盘，保留异步下载契约。</p>
     *
     * @param request 导出请求（含业务筛选）
     * @return 导出任务文件编码，供前端 {@code queryExport} 轮询下载
     */
    public String createMapperExportTask(QueryListRequest<RoutingEntity> request) {
        RoutingEntity filter = request == null || request.getFilter() == null
                ? new RoutingEntity() : request.getFilter();
        routingReadShadowService.submit(filter);
        List<RoutingEntity> rows = routingMapper.selectForExport(filter);
        List<RoutingEntity> masked = ExportColumnMasker.maskForExport(rows);
        return exportHelper.createExportTask(() -> {
            InputStream stream = CollectionUtil.toCsvInputStream(CsvFormulaSanitizer.sanitize(masked), RoutingEntity.class);
            return exportHelper.saveExportFileStream(stream);
        });
    }

    @Override
    protected void afterAppUpdate(long id, @NotNull RoutingEntity source) {
        MesServices.getRoutingOperationService().deleteByRoutingId(id);
        afterAdd(id, source);
    }

    @Override
    protected void afterAppAdd(long id, @NotNull RoutingEntity source) {
        List<RoutingOperationEntity> routingOperationList = source.getDetails();
        RoutingOperationService routingOperationService = MesServices.getRoutingOperationService();
        for (RoutingOperationEntity routingOperation : routingOperationList) {
            routingOperation.setRoutingId(id);
            routingOperationService.add(routingOperation);
        }
    }

    @Override
    protected RoutingEntity afterAppGet(@NotNull RoutingEntity routing) {
        RoutingOperationEntity filter = new RoutingOperationEntity().setRoutingId(routing.getId());
        List<RoutingOperationEntity> details = MesServices.getRoutingOperationService().filter(filter, new Sort()
                .setField(ORDER_FIELD_NAME)
                .setDirection(Sort.ASC)
        );
        routing.setDetails(details);
        return routing;
    }

    @Override
    protected RoutingEntity beforeAppSaveToDatabase(@NotNull RoutingEntity routing) {
        if (Objects.isNull(routing.getIsRoutingBom())) {
            return routing;
        }
        if (!routing.getIsRoutingBom()) {
            routing.setBom(null);
            return routing;
        }
        PARAM_INVALID.whenNull(routing.getBom(), "请配置工艺使用的 BOM");
        return routing;
    }

    @Override
    protected void beforePublish(@NotNull RoutingEntity routing) {
        FORBIDDEN_EDIT.when(CollectionUtils.isEmpty(routing.getDetails()), "发布失败，工艺没有任何工序流程");
    }
}
