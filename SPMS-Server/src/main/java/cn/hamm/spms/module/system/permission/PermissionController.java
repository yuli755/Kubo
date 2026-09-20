package cn.hamm.spms.module.system.permission;

import cn.hamm.spms.framework.util.Trees;
import cn.hamm.airpower.api.annotation.Api;
import cn.hamm.airpower.core.annotation.Description;
import cn.hamm.spms.base.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * <h1>Controller</h1>
 *
 * @author Hamm.cn
 */
@Api("permission")
@Description("权限")
@Tag(name = "权限管理", description = "权限树形结构管理，包含增删改查、发布、树形展示")
public class PermissionController extends BaseController<PermissionEntity, PermissionService, PermissionRepository> {

    @Override
    protected PermissionEntity beforeAdd(@NotNull PermissionEntity permission) {
        return permission.setIsSystem(null);
    }

    @Override
    protected PermissionEntity beforeAppUpdate(@NotNull PermissionEntity permission, @NotNull PermissionEntity exist) {
        return permission.setIsSystem(null);
    }

    @Override
    protected List<PermissionEntity> afterGetList(List<PermissionEntity> list) {
        return Trees.buildTreeList(list);
    }
}
