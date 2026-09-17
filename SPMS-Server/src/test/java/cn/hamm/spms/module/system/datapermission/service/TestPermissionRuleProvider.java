package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.framework.curd.datapermission.PermissionContext;
import cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.framework.curd.datapermission.DataPermissionAction;
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
        // 复用同一份判定：本类是"缓存里的真相"，自己抄一遍就意味着"运行时与预览各判一次"，
        // 而那种不一致在测试里看不出来（测试走的也是副本）
        return DataPermissionSubjectMatcher.matches(policy, context);
    }
}