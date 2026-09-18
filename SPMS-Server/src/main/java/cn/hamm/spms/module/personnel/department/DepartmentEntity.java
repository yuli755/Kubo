package cn.hamm.spms.module.personnel.department;

import cn.hamm.spms.framework.curd.annotation.Unique;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import cn.hamm.spms.core.annotation.Description;
import cn.hamm.spms.core.annotation.Meta;
import cn.hamm.spms.core.interfaces.ITree;
import cn.hamm.spms.framework.curd.annotation.Search;
import cn.hamm.spms.base.TenantAwareEntity;
import cn.hamm.spms.base.coderule.AutoGenerateCode;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.List;

import static cn.hamm.spms.base.coderule.CodeRuleField.DepartmentCode;

/**
 * <h1>实体</h1>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Data
@TableName("department")
@Description("部门")
public class DepartmentEntity extends TenantAwareEntity<DepartmentEntity> implements ITree<DepartmentEntity> {
    @Description("部门编码")
    @Unique
    @AutoGenerateCode(DepartmentCode)
    @Meta
    private String code;

    @Description("部门名称")
    @NotBlank(groups = {WhenUpdate.class, WhenAdd.class}, message = "应用名称不能为空")
    @Search
    @Meta
    private String name;

    @Description("父级 ID")
    @Meta
    private Long parentId;

    @Description("排序号")
    @Meta
    private Integer orderNo;

    @Description("树子集节点数组")
    @TableField(exist = false)
    private List<DepartmentEntity> children;
}
