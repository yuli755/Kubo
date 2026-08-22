package cn.hamm.spms.module.system.datapermission.event;

import cn.hamm.spms.module.system.datapermission.enums.DataPermissionAction;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Set;

/**
 * <h1>数据权限变更事件</h1>
 *
 * @author Hamm.cn
 */
@Data
@Accessors(chain = true)
public class DataPermissionChangedEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 事件类型
     */
    public enum EventType {
        /**
         * 资源变更
         */
        RESOURCE_CHANGED,
        /**
         * 策略变更
         */
        POLICY_CHANGED,
        /**
         * 字段脱敏策略变更
         */
        FIELD_POLICY_CHANGED,
        /**
         * 全量刷新
         */
        FULL_REFRESH
    }

    /**
     * 事件类型
     */
    private EventType eventType;

    /**
     * 资源标识
     */
    private String resourceKey;

    /**
     * 策略 ID（策略变更时）
     */
    private Long policyId;

    /**
     * 影响的动作
     */
    private Set<DataPermissionAction> actions;

    /**
     * 租户 ID（租户级变更时）
     */
    private Long tenantId;

    /**
     * 时间戳
     */
    private long timestamp = System.currentTimeMillis();

    /**
     * 创建资源变更事件
     */
    public static DataPermissionChangedEvent resourceChanged(String resourceKey) {
        return new DataPermissionChangedEvent()
                .setEventType(EventType.RESOURCE_CHANGED)
                .setResourceKey(resourceKey);
    }

    /**
     * 创建策略变更事件
     */
    public static DataPermissionChangedEvent policyChanged(String resourceKey, Long policyId, Set<DataPermissionAction> actions) {
        return new DataPermissionChangedEvent()
                .setEventType(EventType.POLICY_CHANGED)
                .setResourceKey(resourceKey)
                .setPolicyId(policyId)
                .setActions(actions);
    }

    /**
     * 创建字段策略变更事件
     */
    public static DataPermissionChangedEvent fieldPolicyChanged(String resourceKey) {
        return new DataPermissionChangedEvent()
                .setEventType(EventType.FIELD_POLICY_CHANGED)
                .setResourceKey(resourceKey);
    }

    /**
     * 创建全量刷新事件
     */
    public static DataPermissionChangedEvent fullRefresh() {
        return new DataPermissionChangedEvent()
                .setEventType(EventType.FULL_REFRESH);
    }
}