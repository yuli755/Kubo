package cn.hamm.spms.module.system.datapermission.entity;

import cn.hamm.airpower.core.annotation.Description;
import cn.hamm.spms.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

/**
 * <h1>数据字段权限策略</h1>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Entity
@Data
@DynamicInsert
@DynamicUpdate
@Table(name = "data_field_policy", uniqueConstraints = @UniqueConstraint(name = "uk_data_field_policy", columnNames = {"resource_id", "field_name", "role_code"}))
@Description("数据字段权限策略")
public class DataFieldPolicyEntity extends BaseEntity<DataFieldPolicyEntity> {
    @Description("资源 ID")
    @Column(name = "resource_id", nullable = false, insertable = false, updatable = false)
    private Long resourceId;

    @Description("关联资源")
    @ManyToOne
    @jakarta.persistence.JoinColumn(name = "resource_id", nullable = false)
    private DataResourceEntity resource;

    @Description("字段名")
    @Column(name = "field_name", nullable = false, length = 128)
    private String fieldName;

    @Description("角色编码")
    @Column(name = "role_code", nullable = false, length = 128)
    private String roleCode;

    @Description("动作")
    @Column(name = "action_name", nullable = false, length = 16)
    private String actionName;

    @Description("脱敏类型")
    @Column(name = "mask_type", nullable = false, length = 32)
    private String maskType;

    @Description("脱敏参数")
    @Column(name = "mask_expression", length = 512)
    private String maskExpression;

    @Description("是否允许原值")
    @Column(name = "allow_plaintext", nullable = false)
    private Boolean allowPlaintext = false;

    @Description("策略优先级")
    @Column(name = "priority", nullable = false)
    private Integer priority = 0;

    @Description("是否启用")
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;
}
