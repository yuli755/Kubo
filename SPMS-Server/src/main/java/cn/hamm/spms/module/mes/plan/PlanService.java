package cn.hamm.spms.module.mes.plan;

import cn.hamm.airpower.core.interfaces.IDictionary;
import cn.hamm.airpower.curd.model.query.Page;
import cn.hamm.airpower.curd.model.query.QueryPageRequest;
import cn.hamm.airpower.curd.model.query.QueryPageResponse;
import cn.hamm.airpower.curd.model.query.Sort;
import cn.hamm.spms.base.bill.AbstractBaseBillService;
import cn.hamm.spms.module.mes.plan.detail.PlanDetailEntity;
import cn.hamm.spms.module.mes.plan.detail.PlanDetailRepository;
import cn.hamm.spms.module.mes.plan.detail.PlanDetailService;
import cn.hamm.spms.module.mes.plan.enums.PlanStatus;
import cn.hamm.spms.module.mes.plan.mapper.PlanMapper;
import cn.hamm.spms.module.mes.plan.mapper.PlanReadShadowService;
import cn.hamm.spms.module.channel.ChannelServices;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionSpecification;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * <h1>Service</h1>
 *
 * @author Hamm.cn
 */
@Service
public class PlanService extends AbstractBaseBillService<PlanEntity, PlanRepository, PlanDetailEntity, PlanDetailService, PlanDetailRepository> {
    private static final int DEFAULT_READ_PAGE_SIZE = 20;
    private static final int MAX_READ_PAGE_SIZE = 200;

    @Autowired(required = false)
    private PlanMapper planMapper;

    @Autowired
    private PlanReadShadowService planReadShadowService;

    @Autowired
    private DataPermissionSpecification dataPermissionSpecification;

    public boolean isMapperReadEnabled() {
        return planMapper != null;
    }

    /**
     * 生产计划分页读出口；Mapper 关闭时回退 JPA。
     */
    public @NotNull QueryPageResponse<PlanEntity> getMapperPage(QueryPageRequest<PlanEntity> request) {
        QueryPageRequest<PlanEntity> normalized = request == null ? new QueryPageRequest<>() : request;
        if (!isMapperReadEnabled()) {
            return getPage(normalized);
        }
        PlanEntity filter = normalized.getFilter() == null ? new PlanEntity() : normalized.getFilter();
        Page requestedPage = normalized.getPage() == null ? new Page() : normalized.getPage();
        int pageNum = requestedPage.getPageNum() == null ? 1 : requestedPage.getPageNum();
        int pageSize = requestedPage.getPageSize() == null
                ? DEFAULT_READ_PAGE_SIZE : requestedPage.getPageSize();
        if (pageNum < 1 || pageSize < 1) {
            throw new IllegalArgumentException("分页页码和分页大小必须为正数");
        }
        pageSize = Math.min(pageSize, MAX_READ_PAGE_SIZE);
        long offset = ((long) pageNum - 1L) * pageSize;
        if (offset > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("分页页码超出可查询范围");
        }
        planReadShadowService.submit(filter);
        List<PlanEntity> rows = planMapper.selectPageByPermission(filter.getBillCode(), filter.getStatus(),
                filter.getType(), (int) offset, pageSize);
        long total = planMapper.countByPermission(filter.getBillCode(), filter.getStatus(), filter.getType());
        int totalAsInt = Math.toIntExact(total);
        int pageCount = total == 0 ? 0 : Math.toIntExact((total + pageSize - 1L) / pageSize);
        QueryPageResponse<PlanEntity> response = new QueryPageResponse<>();
        response.setList(rows);
        response.setTotal(totalAsInt);
        response.setPageCount(pageCount);
        response.setPage(new Page().setPageNum(pageNum).setPageSize(pageSize));
        response.setSort(normalized.getSort() == null ? new Sort() : normalized.getSort());
        return response;
    }

    /**
     * 生产计划详情读出口；主表通过权限链后再读取明细和客户。
     */
    public PlanEntity getMapperDetail(long id) {
        if (!isMapperReadEnabled()) {
            return get(id);
        }
        planReadShadowService.submit(new PlanEntity());
        PlanEntity bill = planMapper.selectDetailByPermission(id);
        if (bill == null) {
            return null;
        }
        if (detailService != null) {
            bill.setDetails(detailService.getAllByBillId(id));
        }
        if (bill.getCustomer() != null && bill.getCustomer().getId() != null) {
            bill.setCustomer(ChannelServices.getCustomerService().get(bill.getCustomer().getId()));
        }
        return bill;
    }

    @Override
    protected @NotNull List<Predicate> addSearchPredicate(
            @NotNull Root<PlanEntity> root,
            @NotNull CriteriaBuilder builder,
            @NotNull PlanEntity search
    ) {
        List<Predicate> predicateList = new ArrayList<>();
        List<Predicate> dataPermissionPredicates = dataPermissionSpecification.buildPredicates(
                root, builder, "mes.plan", DataPermissionAction.QUERY
        );
        predicateList.addAll(dataPermissionPredicates);
        return predicateList;
    }

    @Override
    public IDictionary getAuditedStatus() {
        return PlanStatus.PRODUCING;
    }

    @Override
    public IDictionary getAuditingStatus() {
        return PlanStatus.AUDITING;
    }

    @Override
    public IDictionary getRejectedStatus() {
        return PlanStatus.REJECTED;
    }

    @Override
    public IDictionary getBillDetailsFinishStatus() {
        return PlanStatus.DONE;
    }
}