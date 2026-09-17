package cn.hamm.spms.module.system.datapermission.event;

import cn.hamm.spms.core.Json;
import cn.hamm.airpower.redis.RedisHelper;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.service.DataFieldPolicyService;
import cn.hamm.spms.module.system.datapermission.service.JpaPermissionRuleProvider;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
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
    private final cn.hamm.spms.module.personnel.department.DepartmentHierarchyIndex departmentHierarchyIndex;
    private final cn.hamm.spms.module.factory.storage.StorageHierarchyIndex storageHierarchyIndex;

    private static final String CHANNEL = "spms:datapermission:changes";

    /**
     * 订阅容器。必须在独立线程里 subscribe：Connection#subscribe 会阻塞当前线程直到退订，
     * 直接在 ApplicationRunner 主线程调用会永久卡死后续的 CommandLineRunner
     * （包括 DevDataInitRunner 的数据初始化与 ApplicationReadyEvent）。
     */
    private volatile RedisMessageListenerContainer listenerContainer;

    @Override
    public void run(ApplicationArguments args) {
        // 启动时订阅频道（异步：容器自带监听线程，不阻塞启动主线程）
        RedisTemplate<String, Object> redisTemplate = redisHelper.getRedisTemplate();
        if (redisTemplate != null && redisTemplate.getConnectionFactory() != null) {
            RedisMessageListenerContainer container = new RedisMessageListenerContainer();
            container.setConnectionFactory(redisTemplate.getConnectionFactory());
            container.addMessageListener(this, new ChannelTopic(CHANNEL));
            container.afterPropertiesSet();
            container.start();
            listenerContainer = container;
            log.info("已订阅数据权限变更频道: {}", CHANNEL);
        }
    }

    @PreDestroy
    public void shutdownSubscription() {
        if (listenerContainer != null) {
            listenerContainer.stop();
            listenerContainer = null;
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
                departmentHierarchyIndex.invalidate();
                storageHierarchyIndex.invalidate();
            }
            case DEPARTMENT_TREE_CHANGED -> {
                // 这是权限链路里唯一带跨节点缓存的一段。不失效就会出现"部门被挪走了，某个节点还按旧树
                // 多放行一片数据"—— 越权方向不能只靠 TTL 兜，所以显式广播
                departmentHierarchyIndex.invalidate();
            }
            case STORAGE_TREE_CHANGED -> {
                // 工厂维度（D13）同理：storage 树被挪动后，工厂闭包快照不失效就会按旧树多放行
                storageHierarchyIndex.invalidate();
            }
        }
    }
}