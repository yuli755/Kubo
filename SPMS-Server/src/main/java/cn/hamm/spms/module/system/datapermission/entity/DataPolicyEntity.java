package cn.hamm.spms.module.system.datapermission.entity;

import cn.hamm.spms.framework.web.annotation.Description;
import cn.hamm.spms.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

/**
 * <h1>数据权限策略</h1>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Entity
@Data
@DynamicInsert
@DynamicUpdate
@Table(name = "data_policy", uniqueConstraints = @UniqueConstraint(name = "uk_data_policy_code", columnNames = "policy_code"))
@Description("数据权限策略")
public class DataPolicyEntity extends BaseEntity<DataPolicyEntity> {

    @Description("策略编码")
    @Column(name = "policy_code", nullable = false, length = 128)
    private String policyCode;

    @Description("资源 ID")
    // 与 DataFieldPolicyEntity 同源问题：data_resource.id 是 bigint UNSIGNED，此处原先未声明
    // columnDefinition，JPA 建出有符号 bigint，与 V2 的 `resource_id BIGINT UNSIGNED NOT NULL`
    // 分叉。本实体没有 @ManyToOne，Hibernate 不会尝试建外键（所以启动日志里没有它的报错），
    // 但类型仍须对齐：一是遵守「迁移脚本 / 实体 / H2 schema 三处同源」，二是 V12 会为其
    // 补建 fk_data_policy_resource，符号性不一致时那条 ALTER 会直接失败。
    @Column(name = "resource_id", nullable = false,
            columnDefinition = "bigint UNSIGNED NOT NULL comment '数据资源ID'")
    private Long resourceId;

    @Description("主体类型")
    @Column(name = "subject_type", nullable = false, length = 16)
    private String subjectType;

    @Description("主体 ID")
    @Column(name = "subject_id")
    private Long subjectId;

    @Description("角色编码")
    @Column(name = "role_code", length = 128)
    private String roleCode;

    @Description("动作")
    @Column(name = "action_name", nullable = false, length = 16)
    private String actionName;

    @Description("条件表达式")
    @Column(name = "condition_json", nullable = false, columnDefinition = "json")
    private String conditionJson = "{}";

    @Description("策略优先级")
    @Column(name = "priority", nullable = false)
    private Integer priority = 0;

    @Description("是否启用")
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Description("配置版本")
    @Column(name = "version_no", nullable = false)
    private Long versionNo = 1L;
}
