package cn.hamm.spms.module.system.datapermission.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import cn.hamm.spms.core.annotation.Description;
import cn.hamm.spms.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <h1>数据权限策略</h1>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Data
@TableName("data_policy")
@Description("数据权限策略")
public class DataPolicyEntity extends BaseEntity<DataPolicyEntity> {

    @Description("策略编码")
    private String policyCode;

    @Description("资源 ID")
    private Long resourceId;

    @Description("主体类型")
    private String subjectType;

    @Description("主体 ID")
    private Long subjectId;

    @Description("角色编码")
    private String roleCode;

    @Description("动作")
    private String actionName;

    @Description("条件表达式")
    private String conditionJson = "{}";

    @Description("策略优先级")
    private Integer priority = 0;

    @Description("是否启用")
    private Boolean enabled = true;

    @Description("配置版本")
    private Long versionNo = 1L;
}
