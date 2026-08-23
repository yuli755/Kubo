package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.repository.DataPolicyRepository;
import cn.hamm.spms.module.system.datapermission.repository.DataResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <h1>测试用权限规则提供者</h1>
 * <p>
 * 测试环境下不使用缓存，直接查询数据库
 * </p>
 *
 * @author Hamm.cn
 */
@Profile("test")
@Service
@RequiredArgsConstructor
public class TestPermissionRuleProvider implements PermissionRuleProvider {
    private final DataResourceRepository resourceRepository;
    private final DataPolicyRepository policyRepository;

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

    /**
     * 测试环境无缓存，空实现
     */
    public void clearResourceCache(String resourceKey) {
        // no-op: test profile doesn't use caching
    }

    /**
     * 测试环境无缓存，空实现
     */
    public void clearPolicyCache(String resourceKey) {
        // no-op: test profile doesn't use caching
    }

    /**
     * 测试环境无缓存，空实现
     */
    public void clearAllCache() {
        // no-op: test profile doesn't use caching
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