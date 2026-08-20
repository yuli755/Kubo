package cn.hamm.spms.module.mes.plan;

import cn.hamm.airpower.core.interfaces.IDictionary;
import cn.hamm.spms.base.bill.AbstractBaseBillService;
import cn.hamm.spms.module.mes.plan.detail.PlanDetailEntity;
import cn.hamm.spms.module.mes.plan.detail.PlanDetailRepository;
import cn.hamm.spms.module.mes.plan.detail.PlanDetailService;
import cn.hamm.spms.module.mes.plan.enums.PlanStatus;
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
    @Autowired
    private DataPermissionSpecification dataPermissionSpecification;

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