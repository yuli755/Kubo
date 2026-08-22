package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.context.PermissionContextHolder;
import cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionAction;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * <h1>数据权限写入守卫</h1>
 *
 * <p>在新增/修改/删除等写入操作中执行数据权限白名单校验：
 * 仅当当前主体与资源之间存在 {@code CREATE}/{@code UPDATE}/{@code DELETE} 动作的
 * 数据权限策略时才放行，否则拒绝写入。</p>
 *
 * <p>对 {@link PermissionContext#bypassDataPermission()} 的用户（ROOT 等）天然放行。</p>
 *
 * @author Hamm.cn
 */
@Component
public class DataPermissionGuard {

    private final DataPermissionService dataPermissionService;

    public DataPermissionGuard(DataPermissionService dataPermissionService) {
        this.dataPermissionService = dataPermissionService;
    }

    /**
     * 校验当前请求主体是否拥有指定资源、指定写入动作的数据权限
     *
     * @param resourceKey 数据资源标识（如 {@code wms.input}）
     * @param action      写入动作（CREATE / UPDATE / DELETE）
     */
    public void enforce(String resourceKey, DataPermissionAction action) {
        PermissionContext context = PermissionContextHolder.current().orElse(null);
        if (context == null) {
            // 无权限上下文（如系统任务），不做拦截，保持向后兼容
            return;
        }
        if (context.bypassDataPermission()) {
            return;
        }

        List<DataPolicyEntity> policies = dataPermissionService.getPolicies(resourceKey, action, context);
        if (policies.isEmpty()) {
            throw new IllegalStateException(
                    "数据权限不足：当前用户无 " + resourceKey + " 的 " + action.name() + " 权限"
            );
        }
    }
}