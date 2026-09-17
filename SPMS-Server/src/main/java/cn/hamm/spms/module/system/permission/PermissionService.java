package cn.hamm.spms.module.system.permission;

import cn.hamm.spms.core.TreeUtil;
import cn.hamm.spms.framework.curd.permission.PermissionUtil;
import cn.hamm.spms.Application;
import cn.hamm.spms.base.BaseService;
import cn.hamm.spms.module.system.permission.enums.PermissionType;
import cn.hamm.spms.module.system.datapermission.SystemPermissionIdentities;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

import static cn.hamm.spms.common.exception.Errors.FORBIDDEN_DELETE;

/**
 * <h1>Service</h1>
 *
 * @author Hamm.cn
 */
@Service
@Slf4j
public class PermissionService extends BaseService<PermissionEntity, PermissionRepository> {

    public void loadPermission() {
        List<PermissionEntity> permissions = PermissionUtil.scanPermission(Application.class.getPackageName(), PermissionEntity.class);
        for (var permission : permissions) {
            PermissionEntity exist = getPermissionByIdentity(permission.getIdentity());
            long existId;
            if (Objects.isNull(exist)) {
                exist = new PermissionEntity()
                        .setName(permission.getName())
                        .setIdentity(permission.getIdentity())
                        .setIsSystem(true);
                existId = add(exist);
            } else {
                existId = exist.getId();
                exist.setName(permission.getName())
                        .setIdentity(permission.getIdentity())
                        .setIsSystem(true);
                updateToDatabase(exist);
            }
            exist = get(existId);
            for (PermissionEntity subPermission : permission.getChildren()) {
                PermissionEntity existSub = getPermissionByIdentity(subPermission.getIdentity());
                if (Objects.isNull(existSub)) {
                    existSub = new PermissionEntity()
                            .setName(subPermission.getName())
                            .setIdentity(subPermission.getIdentity())
                            .setIsSystem(true)
                            .setParentId(exist.getId());
                    add(existSub);
                } else {
                    existSub.setName(subPermission.getName())
                            .setIdentity(subPermission.getIdentity())
                            .setIsSystem(true)
                            .setParentId(exist.getId());
                    updateToDatabase(existSub);
                }
            }
        }
    }
    /**
     * 确保系统级权限存在，供权限迁移和开发初始化幂等调用。
     *
     * @param identity 权限标识
     * @param name 权限名称
     * @return 已存在或新建的权限
     */
    public PermissionEntity ensureSystemPermission(String identity, String name) {
        PermissionEntity permission = getPermissionByIdentity(identity);
        if (permission == null) {
            permission = new PermissionEntity()
                    .setIdentity(identity)
                    .setName(name)
                    .setIsSystem(true)
                    .setType(PermissionType.API.getKey());
            add(permission);
        } else if (!Boolean.TRUE.equals(permission.getIsSystem())) {
            permission.setIsSystem(true);
            updateToDatabase(permission);
        }
        return permission;
    }

    /**
     * 确保权限治理所需的系统权限存在。
     *
     * <p>四个身份与 {@code SystemPermissionIdentities} 一一对应：少种任何一个，
     * 对应特权（绕过/归属迁移/死信管理）就无法授予非 root 角色，只能靠代码里的
     * isRootUser 兜底，"平台管理员"这一层形同虚设。与
     * {@code docs/database/migration/20260909_...consolidated.sql} +
     * {@code 20260914_system_permission_identities.sql} 保持同一份口径。</p>
     */
    public void ensureDataPermissionSystemPermissions() {
        ensureSystemPermission(SystemPermissionIdentities.ROOT, "系统超级权限");
        ensureSystemPermission(SystemPermissionIdentities.DATA_PERMISSION_BYPASS, "绕过数据权限");
        ensureSystemPermission(SystemPermissionIdentities.DATA_PERMISSION_OWNERSHIP, "迁移数据归属");
        ensureSystemPermission(SystemPermissionIdentities.DATA_PERMISSION_OUTBOX, "权限变更死信管理");
    }
}


