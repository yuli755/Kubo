package cn.hamm.spms.module.system.datapermission.entity;

import cn.hamm.spms.framework.web.annotation.Description;
import cn.hamm.spms.framework.web.annotation.FilterIgnore;
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
 * <h1>权限变更 Outbox 事件</h1>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Entity
@Data
@DynamicInsert
@DynamicUpdate
@Table(name = "permission_change_outbox", uniqueConstraints = @UniqueConstraint(name = "uk_permission_outbox_event", columnNames = "event_key"))
@Description("权限变更消息")
public class PermissionChangeOutboxEntity extends BaseEntity<PermissionChangeOutboxEntity> {
    @Description("事件唯一键")
    @Column(name = "event_key", nullable = false, length = 192)
    private String eventKey;

    @Description("聚合类型")
    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Description("聚合 ID")
    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    @Description("事件类型")
    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Description("事件载荷")
    @Column(name = "payload", nullable = false, columnDefinition = "json")
    private String payload;

    @Description("投递状态")
    @Column(name = "status", nullable = false, length = 16)
    // 初始值只是 INSERT 时的缺省，不是查询条件。死信盘点走的是
    // PermissionChangeOutboxRepository#findByStatusOrderByIdDesc，不经过 buildPredicates，
    // 所以这里加 @FilterIgnore 不影响它；影响的只是"万一有人拿本实体当 filter"的那条路，
    // 而那条路一旦走通就会静默变成 WHERE status='PENDING' AND retry_count=0（永远查不到死信）。
    @FilterIgnore
    private String status = "PENDING";

    @Description("重试次数")
    @Column(name = "retry_count", nullable = false)
    @FilterIgnore
    private Integer retryCount = 0;

    @Description("下次重试时间")
    @Column(name = "next_retry_at")
    private Long nextRetryAt;

    @Description("已处理时间")
    @Column(name = "processed_at")
    private Long processedAt;

    @Description("最后错误")
    @Column(name = "last_error", length = 2000)
    private String lastError;
}
