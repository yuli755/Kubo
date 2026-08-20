package cn.hamm.spms.module.mes.operation;

import cn.hamm.spms.base.BaseService;
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
 * @author zfy
 */
@Service
public class OperationService extends BaseService<OperationEntity, OperationRepository> {
    @Autowired
    private DataPermissionSpecification dataPermissionSpecification;

    @Override
    protected @NotNull List<Predicate> addSearchPredicate(
            @NotNull Root<OperationEntity> root,
            @NotNull CriteriaBuilder builder,
            @NotNull OperationEntity search
    ) {
        List<Predicate> predicateList = new ArrayList<>();
        List<Predicate> dataPermissionPredicates = dataPermissionSpecification.buildPredicates(
                root, builder, "mes.operation", DataPermissionAction.QUERY
        );
        predicateList.addAll(dataPermissionPredicates);
        return predicateList;
    }
}