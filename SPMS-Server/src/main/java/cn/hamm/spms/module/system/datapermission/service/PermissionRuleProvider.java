package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.framework.curd.datapermission.PermissionContext;
import cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.framework.curd.datapermission.DataPermissionAction;

import java.util.List;

/**
 * <h1>权限规则提供者</h1>
 *
 * <p>业务模块只依赖该接口，未来可将实现替换为 Redis 或 gRPC 客户端。</p>
 *
 * @author Hamm.cn
 */
public interface PermissionRuleProvider {
    DataResourceEntity getResource(String resourceKey);

    List<DataPolicyEntity> getPolicies(DataResourceEntity resource, DataPermissionAction action, PermissionContext context);
}
