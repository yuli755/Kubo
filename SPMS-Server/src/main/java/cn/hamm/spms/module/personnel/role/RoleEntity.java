package cn.hamm.spms.module.personnel.role;

import cn.hamm.spms.framework.curd.annotation.Unique;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import cn.hamm.spms.core.annotation.Description;
import cn.hamm.spms.core.annotation.Meta;
import cn.hamm.spms.base.TenantAwareEntity;
import cn.hamm.spms.base.coderule.AutoGenerateCode;
import cn.hamm.spms.module.system.menu.MenuEntity;
import cn.hamm.spms.module.system.permission.PermissionEntity;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.Set;

import static cn.hamm.spms.base.coderule.CodeRuleField.RoleCode;

/**
 * <h1>角色实体</h1>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Data
@TableName("role")
@Description("角色")
public class RoleEntity extends TenantAwareEntity<RoleEntity> implements IRoleAction {
    @Description("角色名称")
    @Unique
    @NotBlank(groups = {WhenUpdate.class, WhenAdd.class}, message = "角色名称不能为空")
    @Meta
    private String name;

    @Description("角色编码")
    @Unique
    @AutoGenerateCode(RoleCode)
    @Meta
    private String code;

    @TableField(exist = false)
    private Set<MenuEntity> menuList;

    @TableField(exist = false)
    private Set<PermissionEntity> permissionList;
}
