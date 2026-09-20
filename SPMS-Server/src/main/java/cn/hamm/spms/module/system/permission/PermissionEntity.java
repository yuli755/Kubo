package cn.hamm.spms.module.system.permission;

import cn.hamm.airpower.core.annotation.Description;
import cn.hamm.airpower.curd.annotation.Search;
import cn.hamm.airpower.curd.permission.IPermission;
import cn.hamm.spms.base.BaseEntity;
import cn.hamm.spms.framework.util.TreeNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import java.util.List;

/**
 * <h1>权限实体</h1>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Entity
@Data
@DynamicInsert
@DynamicUpdate
@Table(name = "permission")
@Description("权限")
@Schema(name = "PermissionEntity", description = "权限实体 - 权限树形结构管理")
public class PermissionEntity extends BaseEntity<PermissionEntity> implements IPermission<PermissionEntity>, TreeNode<PermissionEntity> {
    @Description("权限标识")
    @Column(columnDefinition = "varchar(255) default '' comment '权限标识'", unique = true)
    @Schema(description = "权限唯一标识，如 inventory:create", example = "inventory:create")
    private String identity;

    @Description("系统权限")
    @Column(columnDefinition = "bit(1) default 0 comment '系统权限'")
    @Schema(description = "是否为系统内置权限，系统权限不可删除", example = "false")
    private Boolean isSystem;

    @Description("权限类型")
    @Column(columnDefinition = "int UNSIGNED default 0 comment '权限类型'")
    @Schema(description = "权限类型：1=菜单，2=按钮，3=接口", example = "2")
    private Integer type;

    @Description("子菜单")
    @Transient
    @Schema(description = "子权限列表，树形结构展示时使用")
    private List<PermissionEntity> children;

    @Description("名称")
    @Search
    @Column(columnDefinition = "varchar(255) default '' comment '名称'")
    @Schema(description = "权限显示名称", example = "新建库存")
    private String name;

    @Description("父级 ID")
    @Column(columnDefinition = "bigint UNSIGNED default 0 comment '父级ID'")
    @Schema(description = "父权限 ID，顶级权限为 0", example = "0")
    private Long parentId;
}
