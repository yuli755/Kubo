package cn.hamm.spms.module.asset.contract;

import cn.hamm.spms.base.BaseService;
import cn.hamm.spms.module.asset.contract.enums.ContractStatus;
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

import static cn.hamm.airpower.exception.Errors.FORBIDDEN;

/**
 * <h1>Service</h1>
 *
 * @author Hamm.cn
 */
@Service
public class ContractService extends BaseService<ContractEntity, ContractRepository> {
    @Autowired
    private DataPermissionSpecification dataPermissionSpecification;

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
