package cn.hamm.spms.module.system.datapermission.event;

import cn.hamm.airpower.core.Json;
import cn.hamm.airpower.redis.RedisHelper;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.service.PermissionChangeOutboxRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * <h1>数据权限变更事件发布器</h1>
 * <p>
 * 使用 Redis Pub/Sub 将权限变更事件广播到所有实例
 * </p>
 *
 * <p>P5 第一刀之后，发布先<b>落 outbox 持久化</b>（{@link PermissionChangeOutboxRecorder}，
 * 加入变更落库事务），再走 Redis 广播（实时路径，失败只记 WARN、各节点靠 TTL 自愈）。
 * 持久化与实时两条路径职责不同：outbox 保证「变更不丢」，Redis 保证「多实例即时可见」。</p>
 *
 * @author Hamm.cn
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataPermissionEventPublisher {
    private final RedisHelper redisHelper;
    private final PermissionChangeOutboxRecorder outboxRecorder;
    /**
     * 数据权限变更的 Redis Pub/Sub 频道；实时发布与 Outbox Dispatcher 重投共用同一个频道
     */
    public static final String CHANNEL = "spms:datapermission:changes";

    /**
     * 发布资源变更事件
     */
    public void publishResourceChanged(String resourceKey) {
        publish(DataPermissionChangedEvent.resourceChanged(resourceKey));
    }

    /**
     * 发布策略变更事件
     */
    public void publishPolicyChanged(String resourceKey, Long policyId, Set<DataPermissionAction> actions) {
        publish(DataPermissionChangedEvent.policyChanged(resourceKey, policyId, actions));
    }

    /**
     * 发布字段脱敏策略变更事件
     */
    public void publishFieldPolicyChanged(String resourceKey) {
        publish(DataPermissionChangedEvent.fieldPolicyChanged(resourceKey));
    }

    /**
     * 发布部门树结构变更事件
     */
    public void publishDepartmentTreeChanged() {
        publish(DataPermissionChangedEvent.departmentTreeChanged());
    }

    /**
     * 发布仓库/工厂树结构变更事件（D13）
     */
    public void publishStorageTreeChanged() {
        publish(DataPermissionChangedEvent.storageTreeChanged());
    }

    /**
     * 发布全量刷新事件
     */
    public void publishFullRefresh() {
        publish(DataPermissionChangedEvent.fullRefresh());
    }

    private void publish(DataPermissionChangedEvent event) {
        // 先落 outbox（持久化事实）：加入变更所在的落库事务，变更提交则一起提交、回滚则一起消失。
        // 这里不吞异常 —— 落不下 outbox 宁可让调用方看见，也不静默变成「变更发生但持久化记录丢失」。
        outboxRecorder.record(event);
        try {
            String json = Json.toString(event);
            redisHelper.publish(CHANNEL, json);
            log.debug("发布数据权限变更事件: type={}, resourceKey={}", event.getEventType(), event.getResourceKey());
        } catch (Exception e) {
            log.warn("发布数据权限变更事件失败: {}", e.getMessage(), e);
        }
    }
}