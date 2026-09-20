package cn.hamm.spms.module.asset.contract;

import cn.hamm.spms.common.export.EntityCsvExport;
import cn.hamm.airpower.curd.helper.ExportHelper;
import cn.hamm.airpower.curd.model.query.Page;
import cn.hamm.airpower.curd.model.query.QueryListRequest;
import cn.hamm.airpower.curd.model.query.QueryPageRequest;
import cn.hamm.airpower.curd.model.query.QueryPageResponse;
import cn.hamm.airpower.curd.model.query.Sort;
import cn.hamm.spms.base.BaseService;
import cn.hamm.spms.base.mapper.ReadStat;
import cn.hamm.spms.common.CsvFormulaSanitizer;
import cn.hamm.spms.module.asset.contract.enums.ContractStatus;
import cn.hamm.spms.module.asset.contract.mapper.ContractMapper;
import cn.hamm.spms.module.asset.contract.mapper.ContractReadShadowService;
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

import static cn.hamm.spms.common.exception.Errors.FORBIDDEN;

/**
 * <h1>Service</h1>
 *
 * @author Hamm.cn
 */
@Service
public class ContractService extends BaseService<ContractEntity, ContractRepository> {
    private static final int DEFAULT_READ_PAGE_SIZE = 20;
    private static final int MAX_READ_PAGE_SIZE = 200;

    /**
     * 读侧开关关闭时 Mapper Bean 根本不存在；这里必须是可选注入，才能保留 JPA 回滚路径。
     */
    @Autowired(required = false)
    private ContractMapper contractMapper;

    @Autowired
    private ContractReadShadowService contractReadShadowService;

    @Autowired
    private ExportHelper exportHelper;

    @Autowired
    private DataPermissionSpecification dataPermissionSpecification;

    /**
     * @return 是否已启用 Contract 的 Mapper 读路径
     */
    public boolean isMapperReadEnabled() {
        return contractMapper != null;
    }

    /**
     * 合同分页读出口；写路径仍走 AirPower JPA。
     *
     * @param request 分页请求
     * @return 分页结果
     */
    public @NotNull QueryPageResponse<ContractEntity> getMapperPage(QueryPageRequest<ContractEntity> request) {
        QueryPageRequest<ContractEntity> normalized = request == null ? new QueryPageRequest<>() : request;
        if (!isMapperReadEnabled()) {
            return getPage(normalized);
        }
        ContractEntity filter = normalized.getFilter() == null ? new ContractEntity() : normalized.getFilter();
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
        contractReadShadowService.submit(filter);
        List<ContractEntity> list = contractMapper.selectPageByPermission(filter, offset, pageSize);
        long total = contractMapper.countByPermission(filter);
        int totalAsInt = Math.toIntExact(total);
        int pageCount = total == 0 ? 0 : Math.toIntExact((total + pageSize - 1L) / pageSize);
        Sort sort = normalized.getSort() == null ? new Sort() : normalized.getSort();
        QueryPageResponse<ContractEntity> response = new QueryPageResponse<>();
        response.setList(list);
        response.setTotal(totalAsInt);
        response.setPageCount(pageCount);
        response.setPage(new Page().setPageNum(pageNum).setPageSize(pageSize));
        response.setSort(sort);
        return response;
    }

    /**
     * 合同详情读出口；无权行由 Mapper 返回 null。
     *
     * <p>附件与参与方是 {@code @ManyToMany}（无外键），主行通过权限链后沿 JPA 关系补齐，
     * 避免为两张中间表手写 JOIN 读语句。</p>
     *
     * @param id 主键
     * @return 详情或 null
     */
    public ContractEntity getMapperDetail(long id) {
        if (!isMapperReadEnabled()) {
            return get(id);
        }
        contractReadShadowService.submit();
        if (contractMapper.selectDetailByPermission(id) == null) {
            return null;
        }
        return get(id);
    }

    /**
     * 合同总数读出口。
     *
     * @param filter 业务筛选
     * @return 权限范围内行数
     */
    public long getMapperCount(ContractEntity filter) {
        ContractEntity normalized = filter == null ? new ContractEntity() : filter;
        if (isMapperReadEnabled()) {
            contractReadShadowService.submit(normalized);
            return contractMapper.countByPermission(normalized);
        }
        return filter(normalized).size();
    }

    /**
     * 合同统计读出口。
     *
     * @param filter 业务筛选
     * @return 权限范围内统计
     */
    public ReadStat getMapperStat(ContractEntity filter) {
        ContractEntity normalized = filter == null ? new ContractEntity() : filter;
        if (isMapperReadEnabled()) {
            contractReadShadowService.submit(normalized);
            return contractMapper.statByPermission(normalized);
        }
        List<ContractEntity> rows = filter(normalized);
        ReadStat stat = new ReadStat();
        stat.setTotal(rows.size());
        stat.setPublishedTotal(rows.stream().filter(row -> Boolean.TRUE.equals(row.getIsPublished())).count());
        return stat;
    }

    /**
     * 合同真实导出出口（Mapper）。
     *
     * <p>行集经 {@link ContractMapper#selectForExport} 走同一条权限拦截链，列级遮蔽在请求线程完成后交给
     * {@link ExportHelper#createExportTask} 落盘，保留异步下载契约。</p>
     *
     * @param request 导出请求（含业务筛选）
     * @return 导出任务文件编码，供前端 {@code queryExport} 轮询下载
     */
    public String createMapperExportTask(QueryListRequest<ContractEntity> request) {
        ContractEntity filter = request == null || request.getFilter() == null
                ? new ContractEntity() : request.getFilter();
        contractReadShadowService.submit(filter);
        List<ContractEntity> rows = contractMapper.selectForExport(filter);
        List<ContractEntity> masked = ExportColumnMasker.maskForExport(rows);
        return exportHelper.createExportTask(() -> {
            InputStream stream = EntityCsvExport.toCsvInputStream(CsvFormulaSanitizer.sanitize(masked), ContractEntity.class);
            return exportHelper.saveExportFileStream(stream);
        });
    }

    @Override
    protected @NotNull List<Predicate> addSearchPredicate(
            @NotNull Root<ContractEntity> root,
            @NotNull CriteriaBuilder builder,
            @NotNull ContractEntity search
    ) {
        List<Predicate> predicateList = new ArrayList<>();
        List<Predicate> dataPermissionPredicates = dataPermissionSpecification.buildPredicates(
                root, builder, "asset.contract", DataPermissionAction.QUERY
        );
        predicateList.addAll(dataPermissionPredicates);
        return predicateList;
    }

    /**
     * 生效合同
     *
     * @param id 合同 ID
     */
    public void enforce(long id) {
        ContractEntity exist = get(id);
        FORBIDDEN.when(ContractStatus.INVALID.notEqualsKey(exist.getStatus()), "该合同状态无法生效");
        exist.setStatus(ContractStatus.EFFECTIVE.getKey());
        updateToDatabase(exist);
    }

    /**
     * 终止合同
     *
     * @param id 合同 ID
     */
    public void stop(long id) {
        ContractEntity exist = get(id);
        FORBIDDEN.when(ContractStatus.EFFECTIVE.notEqualsKey(exist.getStatus()), "该合同状态无法终止");
        exist.setStatus(ContractStatus.TERMINATED.getKey());
        updateToDatabase(exist);
    }
}
