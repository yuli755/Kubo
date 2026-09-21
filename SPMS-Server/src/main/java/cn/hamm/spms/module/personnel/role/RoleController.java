package cn.hamm.spms.module.personnel.role;

import cn.hamm.spms.framework.web.annotation.Api;
import cn.hamm.spms.framework.http.ApiResponse;
import cn.hamm.spms.framework.web.annotation.Description;
import cn.hamm.spms.base.annotation.Extends;
import cn.hamm.spms.base.Curd;
import cn.hamm.spms.base.BaseController;
import org.jetbrains.annotations.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * <h1>Controller</h1>
 *
 * @author Hamm.cn
 */
@Api("role")
@Description("角色")
@Extends({Curd.Disable, Curd.Enable})
public class RoleController extends BaseController<RoleEntity, RoleService, RoleRepository> implements IRoleAction {

    @Description("授权菜单")
    @PostMapping("authorizeMenu")
    public ApiResponse<Void> authorizeMenu(@RequestBody @Validated({WhenAuthorizePermission.class, WhenIdRequired.class}) RoleEntity role) {
        service.update(role);
        return ApiResponse.success("授权菜单成功");
    }

    @Description("授权权限")
    @PostMapping("authorizePermission")
    public ApiResponse<Void> authorizePermission(@RequestBody @Validated({WhenAuthorizePermission.class, WhenIdRequired.class}) RoleEntity role) {
        service.update(role);
        return ApiResponse.success("授权菜单成功");
    }

    @Override
    protected RoleEntity beforeAppUpdate(@NotNull RoleEntity role, @NotNull RoleEntity exist) {
        return role.setMenuList(null).setPermissionList(null);
    }
}
