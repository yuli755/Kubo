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
        return ruleProvider.getPolicies(resource, action, context);
    }

    public boolean shouldDenyByDefault(String resourceKey, PermissionContext context) {
        if (context == null || context.bypassDataPermission()) {
            return false;
        }
        return getPolicies(resourceKey, DataPermissionAction.QUERY, context).isEmpty();
    }
}
