package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.context.PermissionContextHolder;
import cn.hamm.spms.module.system.datapermission.entity.OperationLogEntity;
import cn.hamm.spms.module.system.datapermission.repository.OperationLogRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * <h1>通用操作审计落库记录器</h1>
 *
 * <p>与 {@link DataPermissionDenyLogRecorder} 同一条纪律，缺一不可：</p>
 * <ol>
 *   <li><b>必须异步</b>：审计发生在业务事务内，同步 {@code save} 会被业务回滚一起回滚掉；
 *       异步线程自开事务，业务成败不吞掉这笔证据；</li>
 *   <li><b>落库失败绝不能改变业务结果</b>：缺 Bean 静默、写失败只 WARN——审计坏了不能让「保存」变成 500。</li>
 * </ol>
 *
 * <p><b>身份与快照在业务线程取成值</b>（与 {@link ShadowDiffService} 同一条纪律）：{@link PermissionContext}
 * 挂在 ThreadLocal 上，后台线程去读会读到「上一个占用该线程的请求」的身份；{@code record} 在调用线程上
 * 把 userId / tenantId / 快照取成值，异步体只碰 {@link OperationLogEvent} 这个值对象。</p>
 *
 * @author Hamm.cn
 */
@Slf4j
@Component
public class OperationLogRecorder {

    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_UPDATE = "UPDATE";
    public static final String ACTION_DELETE = "DELETE";

    /**
     * 启用 / 禁用 / 发布：这三个是<b>状态迁移动作</b>，不是「改了一个字段」。
     *
     * <p>为什么要单独立动作而不是复用 {@link #ACTION_UPDATE}：{@code disable} 在多数资源上
     * 就是「下线这条数据」的实际手段 —— 单据类资源被 {@code @Extends(exclude = Curd.Delete)}
     * 挡掉了硬删除，用户能做的只有禁用。如果禁用记成 UPDATE，审计里就分不清
     * 「把备注改了个字」和「把这条供应商停用了」，而后者才是需要追责的那一件。</p>
     *
     * <p>发布同理且更强：{@code publish} 之后记录被冻结（{@code BaseController} 拒绝再改再删），
     * 「谁把它冻结的」必须可查。</p>
     */
    public static final String ACTION_ENABLE = "ENABLE";
    public static final String ACTION_DISABLE = "DISABLE";
    public static final String ACTION_PUBLISH = "PUBLISH";

    /**
     * 一条操作审计事件（值对象：全部字段在业务线程上定稿，后台线程只做持久化）
     */
    public record OperationLogEvent(String resourceKey,
                                    String action,
                                    Long targetId,
                                    String targetLabel,
                                    String beforeSnapshot,
                                    String afterSnapshot,
                                    Long userId,
                                    Long tenantId,
                                    Long occurredAt) {

        /**
         * 兼容构造器：不显式给发生时刻时按「现在」定稿。
         *
         * <p>留这个重载是为了不把既有的 8 参调用点（含单测）全量改一遍，同时让新代码
         * 仍然走「时间戳在业务线程上定稿」这条契约。</p>
         */
        public OperationLogEvent(String resourceKey,
                                 String action,
                                 Long targetId,
                                 String targetLabel,
                                 String beforeSnapshot,
                                 String afterSnapshot,
                                 Long userId,
                                 Long tenantId) {
            this(resourceKey, action, targetId, targetLabel, beforeSnapshot, afterSnapshot,
                    userId, tenantId, System.currentTimeMillis());
        }
    }

    private final ObjectProvider<OperationLogRepository> repositoryProvider;

    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            1, 1, 60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(128),
            runnable -> {
                Thread thread = new Thread(runnable, "operation-log");
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.AbortPolicy());

    public OperationLogRecorder(ObjectProvider<OperationLogRepository> repositoryProvider) {
        this.repositoryProvider = repositoryProvider;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    /**
     * 记一次操作审计（异步；失败与缺表都只影响证据、不影响业务）
     *
     * @param resourceKey 资源标识
     * @param action      动作（{@link #ACTION_CREATE}/{@link #ACTION_UPDATE}/{@link #ACTION_DELETE}/
     *                    {@link #ACTION_ENABLE}/{@link #ACTION_DISABLE}/{@link #ACTION_PUBLISH}）
     * @param targetId    目标记录 ID
     * @param before      变更前实体（可为 null）
     * @param after       变更后实体（可为 null）
     */
    public void record(String resourceKey, String action, Long targetId, Object before, Object after) {
        OperationLogEvent event = toEvent(resourceKey, action, targetId, before, after);
        try {
            executor.execute(() -> persist(event));
        }
        catch (RejectedExecutionException ex) {
            log.warn("[operation-log] 落库队列已满，本次操作审计未持久化：resource={} action={}",
                    resourceKey, action);
        }
    }

    /**
     * 在<em>业务线程</em>上把一条操作审计定稿成值对象（身份与快照在此取成值）。
     *
     * <p>单独抽出（包级可见）是为了让单测能同步断言「身份确实来自当前上下文、快照确实来自实体」，
     * 而不必去等异步线程调度。</p>
     */
    OperationLogEvent toEvent(String resourceKey, String action, Long targetId, Object before, Object after) {
        PermissionContext context = PermissionContextHolder.current().orElse(null);
        Long userId = context == null ? null : context.userId();
        Long tenantId = context == null ? null : context.tenantId();
        Object target = after != null ? after : before;
        return new OperationLogEvent(
                resourceKey,
                action,
                targetId,
                OperationLogSnapshot.labelOf(target),
                OperationLogSnapshot.of(before),
                OperationLogSnapshot.of(after),
                userId,
                tenantId,
                // 发生时刻必须在业务线程上取：persist() 跑在单线程队列上，积压时
                // 「落库时刻」会晚于「操作时刻」，审计表的时间就不准了。
                System.currentTimeMillis());
    }

    /**
     * 实际落库（异步线程调用）。包级可见而不是私有：让单测能同步调用它验证「事件真的会被写库」。
     */
    void persist(OperationLogEvent event) {
        OperationLogRepository repository = repositoryProvider.getIfAvailable();
        if (repository == null) {
            return;
        }
        try {
            OperationLogEntity entity = new OperationLogEntity()
                    .setResourceKey(event.resourceKey())
                    .setAction(event.action())
                    .setTargetId(event.targetId())
                    .setTargetLabel(event.targetLabel())
                    .setBeforeSnapshot(event.beforeSnapshot())
                    .setAfterSnapshot(event.afterSnapshot());
            // createTime 不能留空：operation_log 走的是本类的 repository.save，绕过了
            // BaseService 里唯一的赋值点（BaseService.java:553），列默认值 0 会让
            // 审计表的「时间」全部渲染成 1970-01-01 —— 审计表唯一的时间证据失效。
            entity.setCreateTime(event.occurredAt());
            entity.setUserId(event.userId());
            entity.setTenantId(event.tenantId());
            repository.save(entity);
        }
        catch (RuntimeException ex) {
            log.warn("[operation-log] 操作审计落库失败（不影响业务结果）：resource={} action={} {}",
                    event.resourceKey(), event.action(), ex.getMessage());
        }
    }
}
