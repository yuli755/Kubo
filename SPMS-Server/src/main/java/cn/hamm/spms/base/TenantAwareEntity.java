package cn.hamm.spms.base;

import cn.hamm.spms.core.annotation.Description;
import cn.hamm.spms.framework.curd.datapermission.PermissionContext;
import cn.hamm.spms.framework.curd.datapermission.PermissionContextHolder;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.Getter;

/**
 * <h1>带归属的实体基类（T1/T2 表专用）</h1>
 *
 * <p>施工计划原文写的是"BaseEntity 加 {@code tenant_id/creator_id}"，这里刻意**不这么做**：
 * {@code cn.hamm.spms.base.BaseEntity} 被全部 55 个实体继承，而迁移只覆盖 T1(23)+T2(9)=32 张表；
 * 基类一加列，剩下 23 张表在 {@code ddl-auto=validate} 下<b>启动即失败</b>。
 * 因此改成"继承即得归属"的中间基类，接入节奏由每个实体的 {@code extends} 决定。</p>
 *
 * <p>写入侧归属由 {@link #fillAttribution()} 自动填：从服务端 {@link PermissionContext} 取，
 * 不接受前端传值。无上下文（定时任务/迁移脚本）时保持为空，由数据库
 * {@code NOT NULL DEFAULT 1} 落到平台默认租户 —— 这是有意为之：
 * 后台任务写入的数据默认归平台，不让它猜租户。</p>
 *
 * <p><b>这里刻意没有加 Hibernate {@code @Filter}/{@code @FilterDef}</b>：
 * 一是没人 {@code enableFilter}，加上只会制造"已经隔离了"的错觉；
 * 二是 {@code @ParamDef} 的签名在 Hibernate 6 各小版本间变过，属于白付的编译风险。
 * 读链路的行级过滤在 {@code DataPermissionSpecification}（JPA）与 P2 的 MyBatis 拦截器里做。</p>
 *
 * @author Hamm.cn
 */
@Getter
@MappedSuperclass
@Description("带归属的业务实体")
public class TenantAwareEntity<E extends TenantAwareEntity<E>> extends BaseEntity<E> {

    @Description("租户 ID")
    @Column(name = "tenant_id")
    private Long tenantId;

    @Description("创建人 ID（数据归属人，用于 USER 主体策略）")
    @Column(name = "creator_id")
    private Long creatorId;

    /**
     * 写入前补齐归属：只信服务端上下文
     *
     * <p>这是 D1「租户只从服务端 {@code user_tenant} 关系解析、不信任客户端」在<b>写侧</b>的兑现：
     * 无条件用服务端推导值覆盖，而不是「客户端没填才补」——后者等于把 {@code tenant_id}/{@code creator_id}
     * 的最终值交给请求方，PUT 一个别的租户 ID 就能伪造归属（把数据建进/送出自己视野）。</p>
     *
     * <p>无上下文（定时任务 / 迁移脚本）时保持字段现值：那类写入是服务端自己构造的实体，
     * 要么显式设了值、要么留空由数据库 {@code NOT NULL DEFAULT 1} 落平台租户。</p>
     *
     * <p>更新路径的归属恢复不在这里（{@code @PreUpdate} 拿不到库中既存值），
     * 由 {@code BaseService} 回表后交给 {@code AttributionWriteProtection} 恢复。</p>
     */
    @PrePersist
    protected void fillAttribution() {
        PermissionContext context = PermissionContextHolder.current().orElse(null);
        if (context == null) {
            return;
        }
        tenantId = context.tenantId();
        creatorId = context.userId();
    }

    /**
     * @param tenantId 租户 ID
     * @return this
     */
    @SuppressWarnings("UnusedReturnValue")
    public E setTenantId(Long tenantId) {
        this.tenantId = tenantId;
        //noinspection unchecked
        return (E) this;
    }

    /**
     * @param creatorId 创建人 ID
     * @return this
     */
    @SuppressWarnings("UnusedReturnValue")
    public E setCreatorId(Long creatorId) {
        this.creatorId = creatorId;
        //noinspection unchecked
        return (E) this;
    }
}
