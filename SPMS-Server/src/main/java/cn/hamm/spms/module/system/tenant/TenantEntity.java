package cn.hamm.spms.module.system.tenant;

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
 * <h1>租户</h1>
 *
 * <p>真多租户（决策 D1/D2）的租户主数据。
 * 表结构见 {@code docs/database/migration/20260906_multitenant_foundation.sql}，
 * 两侧字段必须严格一致（生产 {@code ddl-auto=validate}，不一致会启动失败）。</p>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Entity
@Data
@DynamicInsert
@DynamicUpdate
@Table(name = "tenant", uniqueConstraints = @UniqueConstraint(name = "uk_tenant_code", columnNames = "code"))
@Description("租户")
public class TenantEntity extends BaseEntity<TenantEntity> {
    @Description("租户编码")
    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Description("租户名称")
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Description("状态")
    @Column(name = "status", nullable = false, columnDefinition = "int UNSIGNED default 1 comment '1=启用 0=停用'")
    private Integer status = 1;

    @Description("到期时间")
    @Column(name = "expire_time")
    private Long expireTime;
}
