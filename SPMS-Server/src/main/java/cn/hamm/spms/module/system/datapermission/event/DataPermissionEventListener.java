package cn.hamm.spms.module.system.datapermission.event;

import cn.hamm.airpower.core.Json;
import cn.hamm.airpower.redis.RedisHelper;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.service.DataFieldPolicyService;
import cn.hamm.spms.module.system.datapermission.service.JpaPermissionRuleProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * <h1>数据权限变更事件监听器</h1>
 * <p>
 * 订阅 Redis Pub/Sub 频道，收到事件后清理本地缓存
 * </p>
 *
 * @author Hamm.cn
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class DataPermissionEventListener implements ApplicationRunner, MessageListener {
    private final RedisHelper redisHelper;
    private final JpaPermissionRuleProvider ruleProvider;
    private final DataFieldPolicyService dataFieldPolicyService;

    private static final String CHANNEL = "spms:datapermission:changes";

    @Override
    public void run(ApplicationArguments args) {
        // 启动时订阅频道
        RedisTemplate<String, Object> redisTemplate = redisHelper.getRedisTemplate();
        if (redisTemplate != null) {
            redisTemplate.getConnectionFactory().getConnection().subscribe(this, CHANNEL.getBytes());
            log.info("已订阅数据权限变更频道: {}", CHANNEL);
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody());
            DataPermissionChangedEvent event = Json.parse(json, DataPermissionChangedEvent.class);
            if (event != null) {
                handleEvent(event);
            }
        } catch (Exception e) {
            log.warn("处理数据权限变更事件失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 也支持 Spring 事件总线本地发布
     */
    @EventListener
    public void onLocalEvent(DataPermissionChangedEvent event) {
        handleEvent(event);
    }

    private void handleEvent(DataPermissionChangedEvent event) {
        log.info("收到数据权限变更事件: type={}, resourceKey={}", event.getEventType(), event.getResourceKey());

        switch (event.getEventType()) {
            case RESOURCE_CHANGED -> {
                ruleProvider.clearResourceCache(event.getResourceKey());
            }
            case POLICY_CHANGED -> {
                ruleProvider.clearPolicyCache(event.getResourceKey());
            }
            case FIELD_POLICY_CHANGED -> {
                dataFieldPolicyService.clearCache(event.getResourceKey());
            }
            case FULL_REFRESH -> {
                ruleProvider.clearAllCache();
                dataFieldPolicyService.clearAllCache();
            }
        }
    }
}