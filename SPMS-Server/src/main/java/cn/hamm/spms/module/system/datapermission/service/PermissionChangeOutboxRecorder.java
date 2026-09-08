package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.airpower.core.Json;
import cn.hamm.spms.module.system.datapermission.entity.PermissionChangeOutboxEntity;
import cn.hamm.spms.module.system.datapermission.event.DataPermissionChangedEvent;
import cn.hamm.spms.module.system.datapermission.repository.PermissionChangeOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * <h1>权限变更 Outbox 记录器（P5 第一刀：只落库，不派发）</h1>
 *
 * <p>在权限变更发生时，把事件<b>持久化</b>到 {@code permission_change_outbox}。这是 C7/P5
 * 「变更与 outbox 同事务写入」的写侧半块：{@code record} 直接 {@code repository.save}，
 * 加入调用方（{@code DataPermissionEventPublisher#publish}，挂在各 Service 的
 * {@code afterAppUpdate / beforeDelete} 里）所处的落库事务 —— 变更提交则 outbox 一并提交，
 * 变更回滚则 outbox 一并消失。真正的 Dispatcher / 消费幂等 / 死信重放仍在后置（需真实 MQ），
 * 本类<b>不冒充已派发</b>。</p>
 *
 * <p>幂等键：{@code event_key} 每条事件唯一（类型 + 作用域 + 时间戳 + 单调后缀），
 * 表上的 {@code uk_permission_outbox_event} 是「同一事件不会重复落两行」的兜底，
 * 不是「同一逻辑变更只落一行」——缓存失效本就可重放，去重反而会丢掉第二次变更的失效广播。</p>
 *
 * <p>失败取向：除重复键（幂等跳过）外，{@code record} 不吞异常 —— 落不下 outbox 就要让
 * 调用方看见，而不是静默变成「变更发生了但持久化记录丢了」。</p>
 *
 * @author Hamm.cn
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionChangeOutboxRecorder {

    private final PermissionChangeOutboxRepository repository;

    /**
     * 把一条权限变更事件落成 outbox 行
     *
     * @param event 变更事件
     */
    public void record(DataPermissionChangedEvent event) {
        PermissionChangeOutboxEntity row = new PermissionChangeOutboxEntity()
                .setEventKey(eventKeyOf(event))
                .setAggregateType(aggregateTypeOf(event))
                .setAggregateId(aggregateIdOf(event))
                .setEventType(event.getEventType().name())
                .setPayload(Json.toString(event))
                .setStatus(PermissionChangeOutboxStatus.PENDING.name())
                .setRetryCount(0);
        try {
            repository.save(row);
        }
        catch (DataIntegrityViolationException ex) {
            log.debug("重复的权限变更事件（同 eventKey），幂等跳过: {}", ex.getMessage());
        }
    }

    static String eventKeyOf(DataPermissionChangedEvent event) {
        String scope = switch (event.getEventType()) {
            case POLICY_CHANGED -> "policy:" + event.getPolicyId();
            case RESOURCE_CHANGED -> "resource:" + event.getResourceKey();
            case FIELD_POLICY_CHANGED -> "field-policy:" + event.getResourceKey();
            case DEPARTMENT_TREE_CHANGED -> "department-tree";
            case FULL_REFRESH -> "full-refresh";
        };
        return event.getEventType().name() + "|" + scope + "|" + event.getTimestamp() + "|" + System.nanoTime();
    }

    static String aggregateTypeOf(DataPermissionChangedEvent event) {
        return switch (event.getEventType()) {
            case RESOURCE_CHANGED -> "DATA_RESOURCE";
            case POLICY_CHANGED -> "DATA_POLICY";
            case FIELD_POLICY_CHANGED -> "DATA_FIELD_POLICY";
            case DEPARTMENT_TREE_CHANGED -> "DEPARTMENT_TREE";
            case FULL_REFRESH -> "FULL_REFRESH";
        };
    }

    static long aggregateIdOf(DataPermissionChangedEvent event) {
        // 本层只有「策略」携带数值主键；其余事件（资源/字段策略/部门树/全量）按 key 定位，聚合 ID 记 0
        return event.getEventType() == DataPermissionChangedEvent.EventType.POLICY_CHANGED
                && event.getPolicyId() != null ? event.getPolicyId() : 0L;
    }
}
