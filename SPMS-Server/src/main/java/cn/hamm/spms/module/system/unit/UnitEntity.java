package cn.hamm.spms.module.system.unit;

import cn.hamm.spms.framework.curd.annotation.Unique;
import com.baomidou.mybatisplus.annotation.TableName;
import cn.hamm.spms.core.annotation.Meta;
import cn.hamm.spms.core.annotation.Description;
import cn.hamm.spms.framework.curd.annotation.Search;
import cn.hamm.spms.base.BaseEntity;
import cn.hamm.spms.base.coderule.AutoGenerateCode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import static cn.hamm.spms.base.coderule.CodeRuleField.UnitCode;

/**
 * <h1>单位实体</h1>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Data
@TableName("unit")
@Description("单位")
public class UnitEntity extends BaseEntity<UnitEntity> {
    @Description("单位名称")
    @Unique
    @Search
    @Meta
    private String name;

    @Description("单位编码")
    @Unique
    @AutoGenerateCode(UnitCode)
    @Meta
    private String code;
}
