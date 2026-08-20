package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.personnel.user.UserEntity;
import cn.hamm.spms.module.system.datapermission.SystemPermissionIdentities;
import cn.hamm.spms.module.system.permission.PermissionEntity;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * <h1>系统权限判断服务</h1>
 *
 * @author Hamm.cn
 */
@Service
public class SystemPermissionService {
    public boolean has(UserEntity user, String permissionIdentity) {
        if (user == null || permissionIdentity == null || user.getRoleList() == null) {
            return false;
        }
        return user.getRoleList().stream()
                .filter(Objects::nonNull)
                .flatMap(role -> role.getPermissionList() == null ? java.util.stream.Stream.empty() : role.getPermissionList().stream())
                .filter(Objects::nonNull)
                .map(PermissionEntity::getIdentity)
                .anyMatch(permissionIdentity::equals);
    }

    public boolean isRoot(UserEntity user) {
        return has(user, SystemPermissionIdentities.ROOT);
    }
}
