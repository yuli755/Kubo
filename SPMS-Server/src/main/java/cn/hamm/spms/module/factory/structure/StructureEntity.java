package cn.hamm.spms.module.factory.structure;

import cn.hamm.spms.framework.curd.annotation.Unique;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import cn.hamm.spms.core.annotation.Description;
import cn.hamm.spms.core.annotation.Dictionary;
import cn.hamm.spms.core.annotation.Export;
import cn.hamm.spms.core.annotation.Meta;
import cn.hamm.spms.core.interfaces.ITree;
import cn.hamm.spms.framework.curd.annotation.Search;
import cn.hamm.spms.base.TenantAwareEntity;
import cn.hamm.spms.base.coderule.AutoGenerateCode;
import cn.hamm.spms.module.factory.structure.enums.StructureType;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Set;

import static cn.hamm.spms.base.coderule.CodeRuleField.StructureCode;

/**
 * <h1>生产单元实体</h1>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Data
@TableName("structure")
@Description("生产单元")
public class StructureEntity extends TenantAwareEntity<StructureEntity> implements ITree<StructureEntity> {
    @Description("生产单元编码")
    @Unique
    @AutoGenerateCode(StructureCode)
    @Meta
    @Export
    private String code;

    @Description("名称")
    @Search
    @Length(max = 200, message = "名称最多允许{max}个字符")
    @NotBlank(groups = {WhenUpdate.class, WhenAdd.class}, message = "名称不能为空")
    @Meta
    @Export
    private String name;

    @Description("生产单元类型")
    @Dictionary(value = StructureType.class, groups = {WhenAdd.class, WhenUpdate.class})
    @Export(Export.Type.DICTIONARY)
    private Integer type;

    @Description("父级 ID")
    @Meta
    private Long parentId;

    @Description("树子集节点数组")
    @TableField(exist = false)
    private List<StructureEntity> children;
}
