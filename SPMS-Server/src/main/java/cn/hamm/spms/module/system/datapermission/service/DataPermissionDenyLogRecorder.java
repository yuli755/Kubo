package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import cn.hamm.spms.framework.curd.datapermission.PermissionContext;
import cn.hamm.spms.framework.curd.datapermission.PermissionContextHolder;
import cn.hamm.spms.module.system.datapermission.entity.DataPermissionDenyLogEntity;
import cn.hamm.spms.module.system.datapermission.repository.DataPermissionDenyLogRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * <h1>数据权限拒绝事件的落库记录器</h1>
 *
 * <p>拒绝路径（{@code DataPermissionDenial.denied} → 抛 403）上落库有两个硬约束，缺一不可：</p>
 * <ol>
 *   <li><b>必须异步</b>：写侧拒绝发生在业务事务内，随后 {@code FORBIDDEN.show} 抛异常会让事务回滚 ——
 *       同步 {@code save} 会把拒绝事件一起回滚掉，等于这笔账从没记过。异步线程没有事务上下文，
 *       {@code save} 自开事务，业务回滚不影响证据；</li>
 *   <li><b>落库失败绝不能改变拒绝结果</b>：审计坏了不能让「拒答」变成「放行」或「500」，
 *       所以缺 Bean 静默、写失败只 WARN（Micrometer 计数与 WARN 日志仍在同步路径上，是落库之外的兜底）。</li>
 * </ol>
 *
 * <p><b>身份在业务线程取成值</b>（与 {@link ShadowDiffService} 同一条纪律）：{@link PermissionContext}
 * 挂在 ThreadLocal 上，后台线程去读会读到「上一个占用该线程的请求」的身份。{@code record} 在调用线程上
 * 把 userId / tenantId / mode 取成值，异步体只碰 {@link DenyEvent} 这个值对象。</p>
 *
 * @author Hamm.cn
 */
@Slf4j
@Component
public class DataPermissionDenyLogRecorder {

    /**
     * 一条拒绝事件（值对象：全部字段在业务线程上定稿，后台线程只做持久化）
     */
    public record DenyEvent(String resourceKey,
                            String reason,
                            String mode,
                            Long userId,
                            Long tenantId,
                            String detail) {
    }

    private final DataPermissionProperties properties;
    private final ObjectProvider<DataPermissionDenyLogRepository> repositoryProvider;

    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            1, 1, 60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(128),
            runnable -> {
                Thread thread = new Thread(runnable, "dp-deny-log");
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.AbortPolicy());

    public DataPermissionDenyLogRecorder(DataPermissionProperties properties,
                                         ObjectProvider<DataPermissionDenyLogRepository> repositoryProvider) {
        this.properties = properties;
        this.repositoryProvider = repositoryProvider;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    /**
     * 记一次拒绝事件（异步；失败与缺表都只影响证据、不影响拒绝本身）
     *
     * @param resourceKey 资源标识（可为 null：审计口径里 null 会被归一成 unknown）
     * @param reason      原因串（{@code DataPermissionAudit.REASON_*} / {@code ExportBypassGuard.REASON_*}）
     * @param detail      运维明细（ID、字段名、动作等）
     */
    public void record(String resourceKey, String reason, String detail) {
        DenyEvent event = toEvent(resourceKey, reason, detail);
        try {
            executor.execute(() -> persist(event));
        }
        catch (RejectedExecutionException ex) {
            // 队列满：丢掉这次落库，但必须让人看见「审计有缺口」
            log.warn("[data-permission][deny-log] 落库队列已满，本次拒绝事件未持久化：resource={} reason={}",
                    resourceKey, reason);
        }
    }

    /**
     * 在<em>业务线程</em>上把一条拒绝事件定稿成值对象（身份与档位在此取成值）。
     *
     * <p>单独抽出（包级可见）是为了让单测能同步断言「身份确实来自当前上下文、档位确实来自配置」，
     * 而不必去等异步线程调度 —— 异步体只做 {@code persist}，不做任何判定。</p>
     */
    DenyEvent toEvent(String resourceKey, String reason, String detail) {
        PermissionContext context = PermissionContextHolder.current().orElse(null);
        Long userId = context == null ? null : context.userId();
        Long tenantId = context == null ? null : context.tenantId();
        String mode = properties.effectiveMode(resourceKey).name();
        return new DenyEvent(resourceKey, reason, mode, userId, tenantId, detail);
    }

    /**
     * 实际落库（异步线程调用）。包级可见而不是私有：让单测能同步调用它验证「拒绝事件真的会被写库」，
     * 而不必等待异步线程调度。
     */
    void persist(DenyEvent event) {
        DataPermissionDenyLogRepository repository = repositoryProvider.getIfAvailable();
        if (repository == null) {
            return;
        }
        try {
            DataPermissionDenyLogEntity entity = new DataPermissionDenyLogEntity()
                    .setResourceKey(event.resourceKey())
                    .setReason(event.reason())
                    .setMode(event.mode())
                    .setDetail(event.detail());
            entity.setUserId(event.userId());
            entity.setTenantId(event.tenantId());
            repository.insert(entity);
        }
        catch (RuntimeException ex) {
            log.warn("[data-permission][deny-log] 拒绝事件落库失败（不影响拒绝结果）：resource={} reason={} {}",
                    event.resourceKey(), event.reason(), ex.getMessage());
        }
    }
}
