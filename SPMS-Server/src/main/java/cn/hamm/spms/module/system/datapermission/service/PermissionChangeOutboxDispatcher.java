package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.common.notification.NotificationHelper;
import cn.hamm.spms.common.redis.RedisHelper;
import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import cn.hamm.spms.module.system.datapermission.entity.PermissionChangeOutboxEntity;
// PermissionChangeOutboxStatus 与本类同处 ...datapermission.service 包，无需 import；
// 原 import 误指向不存在的 ...datapermission.enums 包，导致编译期「找不到符号」。
import cn.hamm.spms.module.system.datapermission.event.DataPermissionEventPublisher;
import cn.hamm.spms.module.system.datapermission.repository.PermissionChangeOutboxRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * <h1>权限变更 Outbox Dispatcher（P5 第二刀：拉到期行重投 Redis）</h1>
 *
 * <p>把 {@code permission_change_outbox} 里到期的 {@code PENDING} 行重新投递到 Redis
 * Pub/Sub（与实时发布同一个 {@link DataPermissionEventPublisher#CHANNEL}）。这是「变更不丢」的
 * 恢复路径：实时发布失败 / 进程重启 / Redis 短暂不可用后，由 Dispatcher 兜底重投。</p>
 *
 * <p>默认关闭（{@code kubo.data-permission.outbox.enabled=false}）：本地与测试环境不启动，
 * 真实环境核对表结构与 Redis 后再开。状态迁移只走 {@link OutboxDispatchStep}，
 * 重试阈值与退避只走 {@link PermissionChangeOutboxBackoff} —— 两份纯逻辑，不重抄。</p>
 *
 * <p>失败取向：单行投递失败不影响其余行（各自退避）；Dispatcher 本身抛错由调度器下次重试兜底。
 * 消费端（{@code DataPermissionEventListener}）按事件幂等处理，重复投递无副作用。</p>
 *
 * @author Hamm.cn
 */
@Slf4j
@Component
public class PermissionChangeOutboxDispatcher {

    private static final String LEASE_LOCK_KEY = "lock:schedule:data-permission:outbox:dispatcher";
    private static final long LEASE_LOCK_SECONDS = 10;

    private final DataPermissionProperties properties;
    private final ObjectProvider<PermissionChangeOutboxRepository> repositoryProvider;
    private final ObjectProvider<RedisHelper> redisHelperProvider;

    @Autowired(required = false)
    private NotificationHelper notificationHelper;

    public PermissionChangeOutboxDispatcher(
            DataPermissionProperties properties,
            ObjectProvider<PermissionChangeOutboxRepository> repositoryProvider,
            ObjectProvider<RedisHelper> redisHelperProvider) {
        this.properties = properties;
        this.repositoryProvider = repositoryProvider;
        this.redisHelperProvider = redisHelperProvider;
    }

    /**
     * 轮询拉取到期事件并投递。间隔由 {@code kubo.data-permission.outbox.poll-ms} 覆盖，默认 5s。
     */
    @Scheduled(fixedDelayString = "${kubo.data-permission.outbox.poll-ms:5000}")
    public void dispatchDue() {
        DataPermissionProperties.Outbox outbox = properties.getOutbox();
        if (outbox == null || !outbox.isEnabled()) {
            return;
        }
        PermissionChangeOutboxRepository repository = repositoryProvider.getIfAvailable();
        RedisHelper redisHelper = redisHelperProvider.getIfAvailable();
        if (repository == null || redisHelper == null) {
            log.warn("[data-permission][outbox] Dispatcher 缺依赖（repository={}, redis={}），本轮跳过",
                    repository != null, redisHelper != null);
            return;
        }
        String lockValue = UUID.randomUUID().toString();
        boolean acquired = redisHelper.setIfAbsent(LEASE_LOCK_KEY, lockValue, LEASE_LOCK_SECONDS);
        if (!acquired) {
            log.debug("[data-permission][outbox] 调度租约未获取到，由其他实例执行");
            return;
        }
        try {
            long now = System.currentTimeMillis();
            List<PermissionChangeOutboxEntity> due = repository
                    .findTop100ByStatusAndNextRetryAtLessThanEqualOrderByIdAsc(
                            PermissionChangeOutboxStatus.PENDING.name(), now);
            for (PermissionChangeOutboxEntity row : due) {
                dispatch(repository, redisHelper, row, now);
            }
        } finally {
            redisHelper.releaseLock(LEASE_LOCK_KEY, lockValue);
        }
    }

    private void dispatch(PermissionChangeOutboxRepository repository, RedisHelper redisHelper,
                          PermissionChangeOutboxEntity row, long now) {
        try {
            redisHelper.publish(DataPermissionEventPublisher.CHANNEL, row.getPayload());
            OutboxDispatchStep.Result done = OutboxDispatchStep.markDone(now);
            row.setStatus(done.status());
            if (done.processedAt() != null) {
                row.setProcessedAt(done.processedAt());
            }
        }
        catch (RuntimeException ex) {
            OutboxDispatchStep.Result failed = OutboxDispatchStep.markFailed(row.getRetryCount(), ex.getMessage(), now);
            row.setStatus(failed.status())
                    .setRetryCount(failed.retryCount());
            if (failed.nextRetryAt() != null) {
                row.setNextRetryAt(failed.nextRetryAt());
            }
            if (failed.lastError() != null) {
                row.setLastError(failed.lastError());
            }
            // PermissionChangeOutboxStatus 是无 key 的纯枚举，其状态值口径为 name()
            // （OutboxDispatchStep.markFailed 正是用 DEAD.name() 构造 Result，
            //   permission_change_outbox.status 列也是字符串）。原写法调 .getKey() 编译不过。
            // 比较用 equals 而非 ==：此处虽同为驻留字符串、== 恰好成立，但 status 一旦
            // 从数据库读回就不再驻留，== 会静默失效。
            if (PermissionChangeOutboxStatus.DEAD.name().equals(failed.status()) && notificationHelper != null) {
                notificationHelper.sendError("权限变更Outbox死信告警",
                        String.format("事件 [%s] 投递失败次数达到上限已转入死信队列，错误: %s", row.getEventKey(), ex.getMessage()));
            }
            log.warn("[data-permission][outbox] 投递失败 eventKey={} retry={} status={}: {}",
                    row.getEventKey(), row.getRetryCount(), row.getStatus(), ex.getMessage());
        }
        repository.save(row);
    }
}
