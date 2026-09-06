package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.repository.DataPolicyRepository;
import cn.hamm.spms.module.system.datapermission.repository.DataResourceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <h1>JPA 权限规则提供者</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
@Service
@Profile("!test")
public class JpaPermissionRuleProvider implements PermissionRuleProvider {
    private final DataResourceRepository resourceRepository;
    private final DataPolicyRepository policyRepository;

    public JpaPermissionRuleProvider(DataResourceRepository resourceRepository, DataPolicyRepository policyRepository) {
        this.resourceRepository = resourceRepository;
        this.policyRepository = policyRepository;
    }

    @Override
    @Cacheable(value = "datapermission:resource", key = "#resourceKey")
    public DataResourceEntity getResource(String resourceKey) {
        return resourceRepository.getByResourceKey(resourceKey);
    }

    /**
     * 取该资源该动作下<b>与主体无关</b>的全部启用策略
     *
     * <p>缓存 key 只含 {@code resource.id + action}，因此返回值<b>绝不能</b>已经按当前用户过滤过：
     * 历史实现把 {@code matchesSubject(policy, context)} 放在 {@code @Cacheable} 方法内部，
     * 于是 A 用户算出来的策略列表会被 B 用户直接命中 —— 轻则多/少几条过滤条件，
     * 重则把"别人租户可见的策略"当成"B 也有"，属于权限串号。
     * 正确形状：缓存原始行，主体过滤放在缓存之外（{@link #policiesFor}）。</p>
     */
    @Override
    @Cacheable(value = "datapermission:policies", key = "#resource.id + ':' + #action.name()")
    public List<DataPolicyEntity> getPolicies(DataResourceEntity resource, DataPermissionAction action, PermissionContext context) {
        if (resource == null || resource.getId() == null) {
            return List.of();
        }
        return policyRepository.findByResourceIdAndActionNameAndEnabledTrueOrderByPriorityDesc(resource.getId(), action.name());
    }

    /**
     * 主体相关的策略筛选（无缓存，依赖上下文，必须在缓存边界之外做）
     */
    public List<DataPolicyEntity> policiesFor(DataResourceEntity resource, DataPermissionAction action, PermissionContext context) {
        if (context == null) {
            return List.of();
        }
        return getPolicies(resource, action, context).stream()
                .filter(policy -> matchesSubject(policy, context))
                .toList();
    }

    /**
     * 清理指定资源的缓存
     */
    @CacheEvict(value = "datapermission:resource", key = "#resourceKey")
    public void clearResourceCache(String resourceKey) {
        log.debug("清理资源缓存: {}", resourceKey);
    }

    /**
     * 清理指定资源的策略缓存
     */
    @CacheEvict(value = "datapermission:policies", allEntries = true)
    public void clearPolicyCache(String resourceKey) {
        log.debug("清理策略缓存: {}", resourceKey);
    }

    /**
     * 清理所有缓存
     */
    @CacheEvict(value = {"datapermission:resource", "datapermission:policies"}, allEntries = true)
    public void clearAllCache() {
        log.debug("清理所有数据权限缓存");
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
