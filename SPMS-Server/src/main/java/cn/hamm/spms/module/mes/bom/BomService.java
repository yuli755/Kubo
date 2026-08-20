package cn.hamm.spms.module.mes.bom;

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
 * @author Hamm.cn
 */
@Service
public class BomService extends BaseService<BomEntity, BomRepository> {
    @Autowired
    private DataPermissionSpecification dataPermissionSpecification;

    @Override
    protected @NotNull List<Predicate> addSearchPredicate(
            @NotNull Root<BomEntity> root,
            @NotNull CriteriaBuilder builder,
            @NotNull BomEntity search
    ) {
        List<Predicate> predicateList = new ArrayList<>();
        List<Predicate> dataPermissionPredicates = dataPermissionSpecification.buildPredicates(
                root, builder, "mes.bom", DataPermissionAction.QUERY
        );
        predicateList.addAll(dataPermissionPredicates);
        return predicateList;
    }
}