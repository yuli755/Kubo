package cn.hamm.spms.module.system.menu;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import cn.hamm.spms.core.annotation.Description;
import cn.hamm.spms.core.annotation.Meta;
import cn.hamm.spms.core.interfaces.ITree;
import cn.hamm.spms.framework.curd.annotation.Search;
import cn.hamm.spms.base.BaseEntity;
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
@TableName("menu")
@Description("菜单")
public class MenuEntity extends BaseEntity<MenuEntity> implements ITree<MenuEntity> {
    @Description("名称")
    @Search
    @Length(max = 200, message = "名称最多允许{max}个字符")
    @NotBlank(groups = {WhenUpdate.class, WhenAdd.class}, message = "名称不能为空")
    @Meta
    private String name;

    @Description("父级 ID")
    @Meta
    private Long parentId;

    @Description("菜单路径")
    @Meta
    private String path;

    @Description("组件路径")
    @Meta
    private String component;

    @Description("菜单图标")
    @Meta
    private String icon;

    @Description("排序号")
    @Meta
    private Integer orderNo;

    @Description("权限标识")
    @Search
    @Length(max = 200, message = "权限标识最多允许{max}个字符")
    @Meta
    private String permission;

    @Description("树子集节点数组")
    @TableField(exist = false)
    @Meta
    private List<MenuEntity> children;
}
