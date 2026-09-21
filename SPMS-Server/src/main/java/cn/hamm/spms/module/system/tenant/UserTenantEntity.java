package cn.hamm.spms.module.system.tenant;

import cn.hamm.spms.framework.web.annotation.Description;
import cn.hamm.spms.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

/**
 * <h1>用户-租户关系</h1>
 *
 * <p><b>这是租户 ID 的唯一可信来源</b>（决策 D1）：
 * {@code PermissionContext.tenantId} 必须由本表解析得出，
 * <b>禁止</b>直接采信前端提交的 {@code X-Tenant-Id}。</p>
 *
 * <p>一个用户可属于多个租户（平台运营方、跨厂协作场景），
 * 其中 {@code isDefault} 标记登录后的默认租户；前端切换租户时只能在
 * 本表存在的租户集合内选择，否则按越权处理（403）。</p>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Entity
@Data
@DynamicInsert
@DynamicUpdate
@Table(
        name = "user_tenant",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_tenant", columnNames = {"user_id", "tenant_id"}),
        indexes = @Index(name = "idx_user_tenant_tenant", columnList = "tenant_id")
)
@Description("用户租户关系")
public class UserTenantEntity extends BaseEntity<UserTenantEntity> {
    @Description("用户 ID")
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Description("租户 ID")
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Description("是否默认租户")
    @Column(name = "is_default", nullable = false, columnDefinition = "bit(1) default 0 comment '是否默认租户'")
    private Boolean isDefault = false;
}
