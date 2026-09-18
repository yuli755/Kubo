package cn.hamm.spms.module.system.datapermission.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import cn.hamm.spms.core.annotation.Description;
import cn.hamm.spms.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <h1>数据字段权限策略</h1>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Data
@TableName("data_field_policy")
@Description("数据字段权限策略")
public class DataFieldPolicyEntity extends BaseEntity<DataFieldPolicyEntity> {

    @Description("资源 ID")
    @TableField("resource_id")
    private Long resourceId;

    @Description("关联资源")
    @TableField(exist = false)
    private DataResourceEntity resource;

    @Description("字段名")
    private String fieldName;

    @Description("角色编码")
    private String roleCode;

    @Description("动作")
    private String actionName;

    @Description("脱敏类型")
    private String maskType;

    @Description("脱敏参数")
    private String maskExpression;

    @Description("是否允许原值")
    private Boolean allowPlaintext = false;

    @Description("策略优先级")
    private Integer priority = 0;

    @Description("是否启用")
    private Boolean enabled = true;
}
