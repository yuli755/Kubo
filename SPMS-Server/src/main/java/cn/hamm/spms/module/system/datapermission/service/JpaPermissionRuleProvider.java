package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.repository.DataPolicyRepository;
import cn.hamm.spms.module.system.datapermission.repository.DataResourceRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <h1>JPA 权限规则提供者</h1>
 *
 * @author Hamm.cn
 */
@Service
public class JpaPermissionRuleProvider implements PermissionRuleProvider {
    private final DataResourceRepository resourceRepository;
    private final DataPolicyRepository policyRepository;

    public JpaPermissionRuleProvider(DataResourceRepository resourceRepository, DataPolicyRepository policyRepository) {
        this.resourceRepository = resourceRepository;
        this.policyRepository = policyRepository;
    }

    @Override
    public DataResourceEntity getResource(String resourceKey) {
        return resourceRepository.getByResourceKey(resourceKey);
    }

    @Override
    public List<DataPolicyEntity> getPolicies(DataResourceEntity resource, DataPermissionAction action, PermissionContext context) {
        if (resource == null || resource.getId() == null) {
            return List.of();
        }
        return policyRepository.findByResourceIdAndActionNameAndEnabledTrueOrderByPriorityDesc(resource.getId(), action.name())
                .stream()
                .filter(policy -> matchesSubject(policy, context))
                .toList();
    }

    private boolean matchesSubject(DataPolicyEntity policy, PermissionContext context) {
        return switch (policy.getSubjectType()) {
            case "SYSTEM" -> true;
            case "USER" -> context.userId().equals(policy.getSubjectId());
            case "TENANT" -> context.tenantId().equals(policy.getSubjectId());
            case "ROLE" -> policy.getRoleCode() != null && context.roleCodes().contains(policy.getRoleCode());
            case "DEPARTMENT" -> policy.getSubjectId() != null && context.departmentIds().contains(policy.getSubjectId());
            case "FACTORY" -> policy.getSubjectId() != null && context.factoryIds().contains(policy.getSubjectId());
            case "WAREHOUSE" -> policy.getSubjectId() != null && context.warehouseIds().contains(policy.getSubjectId());
            default -> false;
        };
    }
}
