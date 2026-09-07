package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionAction;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <h1>数据权限决策服务</h1>
 *
 * <p>首期负责资源注册和规则获取，行级 SQL 注入在试点 Mapper 迁移后接入。</p>
 *
 * @author Hamm.cn
 */
@Service
public class DataPermissionService {
    private final PermissionRuleProvider ruleProvider;

    public DataPermissionService(PermissionRuleProvider ruleProvider) {
        this.ruleProvider = ruleProvider;
    }

    public DataResourceEntity requireResource(String resourceKey) {
        DataResourceEntity resource = ruleProvider.getResource(resourceKey);
        if (resource == null || !"ENABLED".equals(resource.getStatus())) {
            throw new IllegalStateException("数据权限资源未注册或未启用: " + resourceKey);
        }
        return resource;
    }

    public List<DataPolicyEntity> getPolicies(String resourceKey, DataPermissionAction action, PermissionContext context) {
        if (context == null) {
            throw new IllegalStateException("数据权限上下文不存在");
        }
        if (context.bypassDataPermission()) {
            return List.of();
        }
        DataResourceEntity resource = requireResource(resourceKey);
        // 主体筛选优先走 provider 的 policiesFor（缓存边界外的方法），
        // 其他实现（如后续 gRPC）若未提供，则退化为"取回后本地筛选"，
        // 但绝不允许"缓存里已经筛过一遍"。
        if (ruleProvider instanceof JpaPermissionRuleProvider jpa) {
            return jpa.policiesFor(resource, action, context);
        }
        return ruleProvider.getPolicies(resource, action, context).stream()
                .filter(policy -> matchesSubject(policy, context))
                .toList();
    }

    /**
     * 兜底实现的主体匹配：直接复用 {@link DataPermissionSubjectMatcher}，与运行时提供者同一份判据
     */
    private boolean matchesSubject(DataPolicyEntity policy, PermissionContext context) {
        // 判定本体在 DataPermissionSubjectMatcher：带缓存的 JpaPermissionRuleProvider 走的是同一份。
        // 这里原来抄了一份，两处的 null 处理还不一致 —— "预览说策略可达、运行时判它不可达"就是这么来的
        return DataPermissionSubjectMatcher.matches(policy, context);
    }

    public boolean shouldDenyByDefault(String resourceKey, PermissionContext context) {
        if (context == null || context.bypassDataPermission()) {
            return false;
        }
        return getPolicies(resourceKey, DataPermissionAction.QUERY, context).isEmpty();
    }
}
