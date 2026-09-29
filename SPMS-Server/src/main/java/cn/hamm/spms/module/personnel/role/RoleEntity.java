package cn.hamm.spms.module.personnel.role;

import cn.hamm.spms.framework.web.annotation.Description;
import cn.hamm.spms.framework.web.annotation.Meta;
import cn.hamm.spms.base.TenantAwareEntity;
import cn.hamm.spms.common.annotation.AutoGenerateCode;
import cn.hamm.spms.module.system.menu.MenuEntity;
import cn.hamm.spms.module.system.permission.PermissionEntity;
import cn.hamm.spms.module.system.datapermission.annotation.OperationLog;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import java.util.Set;

import static cn.hamm.spms.module.system.coderule.enums.CodeRuleField.RoleCode;

/**
 * <h1>角色实体</h1>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Entity
@Data
@DynamicInsert
@DynamicUpdate
@Table(name = "role")
@Description("角色")
@OperationLog
public class RoleEntity extends TenantAwareEntity<RoleEntity> implements IRoleAction {
    @Description("角色名称")
    @Column(columnDefinition = "varchar(255) default '' comment '角色名称'", unique = true)
    @NotBlank(groups = {WhenUpdate.class, WhenAdd.class}, message = "角色名称不能为空")
    @Meta
    private String name;

    @Description("角色编码")
    @Column(columnDefinition = "varchar(255) default '' comment '角色编码'", unique = true)
    @AutoGenerateCode(RoleCode)
    @Meta
    private String code;

    @ManyToMany(fetch = FetchType.LAZY)
    private Set<MenuEntity> menuList;

    @ManyToMany(fetch = FetchType.LAZY)
    private Set<PermissionEntity> permissionList;
}
