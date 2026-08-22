package cn.hamm.spms.module.system.datapermission.event;

import cn.hamm.airpower.core.Json;
import cn.hamm.airpower.redis.RedisHelper;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionAction;
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
 * @author Hamm.cn
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataPermissionEventPublisher {
    private final RedisHelper redisHelper;
    private static final String CHANNEL = "spms:datapermission:changes";

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
     * 发布全量刷新事件
     */
    public void publishFullRefresh() {
        publish(DataPermissionChangedEvent.fullRefresh());
    }

    private void publish(DataPermissionChangedEvent event) {
        try {
            String json = Json.toString(event);
            redisHelper.publish(CHANNEL, json);
            log.debug("发布数据权限变更事件: type={}, resourceKey={}", event.getEventType(), event.getResourceKey());
        } catch (Exception e) {
            log.warn("发布数据权限变更事件失败: {}", e.getMessage(), e);
        }
    }
}