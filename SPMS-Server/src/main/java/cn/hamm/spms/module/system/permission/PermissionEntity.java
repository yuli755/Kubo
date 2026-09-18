package cn.hamm.spms.module.system.permission;

import cn.hamm.spms.framework.curd.annotation.Unique;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import cn.hamm.spms.core.annotation.Description;
import cn.hamm.spms.core.annotation.Dictionary;
import cn.hamm.spms.core.annotation.Meta;
import cn.hamm.spms.framework.curd.annotation.Search;
import cn.hamm.spms.framework.curd.permission.IPermission;
import cn.hamm.spms.base.BaseEntity;
import cn.hamm.spms.module.system.permission.enums.PermissionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * <h1>权限实体</h1>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Data
@TableName("permission")
@Description("权限")
@Schema(name = "PermissionEntity", description = "权限实体 - 权限树形结构管理")
public class PermissionEntity extends BaseEntity<PermissionEntity> implements IPermission<PermissionEntity> {
    @Description("权限标识")
    @Unique
    @Schema(description = "权限唯一标识，如 inventory:create", example = "inventory:create")
    private String identity;

    @Description("系统权限")
    @Schema(description = "是否为系统内置权限，系统权限不可删除", example = "false")
    private Boolean isSystem;

    @Description("权限类型")
    @Schema(description = "权限类型：1=菜单，2=按钮，3=接口", example = "2")
    private Integer type;

    @Description("子菜单")
    @TableField(exist = false)
    @Schema(description = "子权限列表，树形结构展示时使用")
    private List<PermissionEntity> children;

    @Description("名称")
    @Search
    @Schema(description = "权限显示名称", example = "新建库存")
    private String name;

    @Description("父级 ID")
    @Schema(description = "父权限 ID，顶级权限为 0", example = "0")
    private Long parentId;
}
