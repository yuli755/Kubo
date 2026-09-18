package cn.hamm.spms.module.factory.storage;

import cn.hamm.spms.framework.curd.annotation.Unique;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import cn.hamm.spms.core.annotation.Description;
import cn.hamm.spms.core.annotation.Export;
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

import static cn.hamm.spms.base.coderule.CodeRuleField.StorageCode;

/**
 * <h1>仓库实体</h1>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Data
@TableName("storage")
@Description("仓库")
public class StorageEntity extends TenantAwareEntity<StorageEntity> implements ITree<StorageEntity> {
    @Description("仓库编码")
    @Unique
    @AutoGenerateCode(StorageCode)
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

    @Description("父级 ID")
    @Meta
    private Long parentId;

    @Description("树子集节点数组")
    @TableField(exist = false)
    private List<StorageEntity> children;
}
