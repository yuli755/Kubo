package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.personnel.user.UserEntity;
import cn.hamm.spms.module.system.datapermission.SystemPermissionIdentities;
import cn.hamm.spms.module.system.permission.PermissionEntity;
import org.springframework.stereotype.Service;

/**
 * <h1>系统权限判断服务</h1>
 *
 * @author Hamm.cn
 */
@Service
public class SystemPermissionService {
    /**
     * 按权限标识判断用户是否持有某权限
     *
     * <p>比对用 equalsIgnoreCase，与库内口径一致：permission.identity 的唯一索引建在
     * 不区分大小写的排序规则上，{@code Material_getPage} 与 {@code material_getPage}
     * 在库里本就是同一个权限（{@code getByIdentity} 也靠这条 CI 语义才查得到）。
     * 内存里若改用区分大小写的 equals，就与「库里能查到」互相矛盾，凡是从 URL 派生
     * 标识的调用方都会误判无权限（见 RequestInterceptor#checkUserPermission）。</p>
     *
     * <p>禁用态不参与授权：停用角色（B-7）/停用权限是收回授权的常规操作，
     * 角色与权限的 {@code is_disabled} 为 TRUE 时必须跳过——否则「禁用角色」
     * 只是改了个显示状态，已绑定用户的权限一点没少，授权回收形同虚设。</p>
     *
     * @param user               用户（须已初始化 roleList/permissionList 关联）
     * @param permissionIdentity 权限标识
     * @return 是否持有
     */
    public boolean has(UserEntity user, String permissionIdentity) {
        if (user == null || permissionIdentity == null || user.getRoleList() == null) {
            return false;
        }
        return user.getRoleList().stream()
                .filter(role -> role != null && !Boolean.TRUE.equals(role.getIsDisabled()))
                .flatMap(role -> role.getPermissionList() == null ? java.util.stream.Stream.empty() : role.getPermissionList().stream())
                .filter(permission -> permission != null && !Boolean.TRUE.equals(permission.getIsDisabled()))
                .map(PermissionEntity::getIdentity)
                .anyMatch(permissionIdentity::equalsIgnoreCase);
    }

    public boolean isRoot(UserEntity user) {
        return has(user, SystemPermissionIdentities.ROOT);
    }
}
