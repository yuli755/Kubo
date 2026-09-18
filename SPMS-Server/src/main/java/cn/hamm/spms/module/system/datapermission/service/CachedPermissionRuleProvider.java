package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.framework.curd.datapermission.PermissionContext;
import cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.framework.curd.datapermission.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.repository.DataPolicyRepository;
import cn.hamm.spms.module.system.datapermission.repository.DataResourceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <h1>权限规则提供者（带缓存版）</h1>
 *
 * <p>生产档（{@code @Profile("!test")}）的实现：仓储取数 + Spring 缓存注解。测试档的替身是
 * {@code TestPermissionRuleProvider}，两者共用 {@link PermissionRuleProvider} 契约。</p>
 *
 * <p><b>名字史（2026-09-18）</b>：本类原名 {@code JpaPermissionRuleProvider} —— 那时它确实
 * 是走 JPA 的那一份。J4 删掉 JPA 之后，实现体已经是 MyBatis-Plus 仓储（{@code DataResourceRepository}
 * / {@code DataPolicyRepository} 都建在 {@code BaseMapper} 上），名字里的 "Jpa" 只剩误导：
 * 读代码的人会以为仓库里还有一份 JPA 实现。故更名为 {@code CachedPermissionRuleProvider}，
 * 把它的特征（唯一带 Spring 缓存的那个实现）写进名字。</p>
 *
 * @author Hamm.cn
 */
@Slf4j
@Service
@Profile("!test")
public class CachedPermissionRuleProvider implements PermissionRuleProvider {
    private final DataResourceRepository resourceRepository;
    private final DataPolicyRepository policyRepository;

    public CachedPermissionRuleProvider(DataResourceRepository resourceRepository, DataPolicyRepository policyRepository) {
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
        // 复用同一份判定：本类是"缓存里的真相"，自己抄一遍就意味着"运行时与预览各判一次"，
        // 而那种不一致在测试里看不出来（测试走的也是副本）
        return DataPermissionSubjectMatcher.matches(policy, context);
    }
}
