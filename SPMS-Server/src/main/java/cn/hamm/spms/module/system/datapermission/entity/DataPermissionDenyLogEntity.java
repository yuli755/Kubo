package cn.hamm.spms.module.system.datapermission.entity;

import cn.hamm.airpower.core.annotation.Description;
import cn.hamm.spms.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

/**
 * <h1>数据权限拒绝事件</h1>
 *
 * <p>「被数据权限拒了」这件事，此前只有 Micrometer 计数与 WARN 日志两个出口 —— 事后追认
 * （<b>谁</b>在<b>何时</b>、因为<b>什么原因</b>、在<b>哪个档位</b>下被拒）无处可查，而放开 enforce
 * 之后第一个被问的就是这个。本实体是四个拒答出口（{@code DataPermissionGuard} / {@code RowWriteGuard}
 * / {@code ExportBypassGuard} / 读侧恒假条件）统一落下的那笔账。</p>
 *
 * <p><b>语义边界（刻意写清，避免被当成「策略命中审计」）</b>：本仓的策略模型是<em>过滤条件</em>
 * （{@code condition_json}），不是「allow / deny 规则」，因此不存在「某条策略否决了这次请求」这种事件 ——
 * 写侧被拒是因为<em>没有策略</em>或<em>行不在范围</em>，读侧是<em>恒假条件</em>，导出是<em>字段未获明文授权</em>。
 * 这里记的是<em>拒绝事件</em>，原因由 {@code reason} 编码，明细由 {@code detail} 承载；
 * 「具体是哪条策略」不在本表的语义里，别把它当成可回查策略 ID 的审计台。</p>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Entity
@Data
@DynamicInsert
@DynamicUpdate
@Table(name = "data_permission_deny_log")
@Description("数据权限拒绝事件")
public class DataPermissionDenyLogEntity extends BaseEntity<DataPermissionDenyLogEntity> {

    @Description("ID")
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Description("数据资源标识")
    @Column(name = "resource_key", nullable = false, length = 128)
    private String resourceKey;

    @Description("拒绝原因（unregistered-resource / write-without-policy / row-not-writable / export-bypass 等）")
    @Column(name = "reason", nullable = false, length = 64)
    private String reason;

    @Description("拒绝发生时该资源的生效档位（OFF / SHADOW / ENFORCE）")
    @Column(name = "mode", nullable = false, length = 16)
    private String mode;

    @Description("被拒主体用户")
    @Column(name = "user_id")
    private Long userId;

    @Description("被拒主体租户")
    @Column(name = "tenant_id")
    private Long tenantId;

    @Description("运维明细（ID、字段名、动作等）")
    @Column(name = "detail", columnDefinition = "text")
    private String detail;
}
