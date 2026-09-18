package cn.hamm.spms.module.system.coderule;

import cn.hamm.spms.framework.curd.annotation.Unique;
import com.baomidou.mybatisplus.annotation.TableName;
import cn.hamm.spms.core.annotation.Description;
import cn.hamm.spms.core.annotation.Dictionary;
import cn.hamm.spms.core.annotation.Meta;
import cn.hamm.spms.core.annotation.ReadOnly;
import cn.hamm.spms.base.BaseEntity;
import cn.hamm.spms.base.coderule.CodeRuleField;
import cn.hamm.spms.base.coderule.SerialNumberUpdate;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <h1>编码规则实体</h1>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Data
@TableName("coderule")
@Description("编码规则")
public class CodeRuleEntity extends BaseEntity<CodeRuleEntity> {
    @Description("规则字段")
    @Unique
    @NotNull(groups = {WhenUpdate.class, WhenAdd.class}, message = "规则字段不能为空")
    @Dictionary(value = CodeRuleField.class, groups = {WhenUpdate.class, WhenAdd.class})
    @Meta
    private Integer ruleField;

    @Description("流水号更新方式")
    @NotNull(groups = {WhenUpdate.class, WhenAdd.class}, message = "流水号更新方式不能为空")
    @Dictionary(value = SerialNumberUpdate.class, groups = {WhenUpdate.class, WhenAdd.class})
    private Integer snType;

    @Description("流水号起始长度")
    @NotNull(groups = {WhenUpdate.class, WhenAdd.class}, message = "流水号起始长度不能为空")
    @Range(min = 1, max = 10, message = "流水号只允许{min}-{max}位")
    private Integer snLength;

    @Description("编码前缀")
    @NotNull(groups = {WhenUpdate.class, WhenAdd.class}, message = "流水号起始长度不能为空")
    @Length(min = 1, max = 10, message = "编码前缀只允许{min}-{max}位")
    private String prefix;

    @Description("编码规则模板")
    @NotNull(groups = {WhenUpdate.class, WhenAdd.class}, message = "编码规则模板")
    @Length(max = 64, message = "模板最多允许{max}个字符")
    private String template;

    @Description("当前序列号")
    @ReadOnly
    private Integer currentSn;

    @Description("重置年")
    @ReadOnly
    private Integer currentYear;

    @Description("重置月")
    @ReadOnly
    private Integer currentMonth;

    @Description("重置日")
    @ReadOnly
    private Integer currentDay;

    @Description("内置参数")
    @ReadOnly
    private Boolean isSystem;

    /**
     * 设置是系统内置规则
     *
     * @param isSystem 内置规则
     * @return 编码规则
     */
    public CodeRuleEntity setIsSystem(Boolean isSystem) {
        this.isSystem = isSystem;
        return this;
    }
}
