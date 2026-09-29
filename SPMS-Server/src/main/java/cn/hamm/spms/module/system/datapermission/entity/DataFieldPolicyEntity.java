package cn.hamm.spms.module.system.datapermission.entity;

import cn.hamm.spms.framework.web.annotation.Description;
import cn.hamm.spms.framework.web.annotation.FilterIgnore;
import cn.hamm.spms.base.BaseEntity;
import cn.hamm.spms.module.system.datapermission.annotation.OperationLog;
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
 * <p><b>三个带初始值的字段全部标了 {@code @FilterIgnore}</b>，理由见 {@link DataResourceEntity}
 * 的类注释。真机实测库里 4 条列级策略、{@code POST /dataFieldPolicy/getList {"filter":{}}}
 * 回 0 行，「列级脱敏策略」页整页空白。</p>
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
@OperationLog
public class DataFieldPolicyEntity extends BaseEntity<DataFieldPolicyEntity> {

    @Description("资源 ID")
    // UNSIGNED 必须显式声明：data_resource.id 是 bigint UNSIGNED（BaseEntity 的 columnDefinition
    // + V2 的 DDL 都是），而这里原先没写 columnDefinition，JPA 建出的是**有符号** bigint。
    // MySQL 拒绝有符号↔无符号建外键，启动日志里那条唯一的 DDL 失败就是它：
    //   alter table data_field_policy add constraint FK... foreign key (resource_id)
    //     references data_resource (id) -> ... are incompatible
    // 后果是「列级脱敏策略 → 数据资源」的引用完整性在库层静默缺失。
    @Column(name = "resource_id", nullable = false, insertable = false, updatable = false,
            columnDefinition = "bigint UNSIGNED NOT NULL comment '数据资源ID'")
    private Long resourceId;

    @Description("关联资源")
    @ManyToOne
    @jakarta.persistence.JoinColumn(name = "resource_id", nullable = false,
            columnDefinition = "bigint UNSIGNED NOT NULL comment '数据资源ID'")
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
    @FilterIgnore
    private Boolean allowPlaintext = false;

    @Description("策略优先级")
    @Column(name = "priority", nullable = false)
    @FilterIgnore
    private Integer priority = 0;

    @Description("是否启用")
    @Column(name = "enabled", nullable = false)
    @FilterIgnore
    private Boolean enabled = true;
}
