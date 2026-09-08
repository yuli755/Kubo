package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * <h1>数据权限审计与指标</h1>
 *
 * <p>灰度期最重要的不是"拦住多少"，而是"如果开了会拦多少"。
 * 本组件把三类事件统一成可观测数据：</p>
 * <ul>
 *   <li>{@code kubo.datapermission.deny} —— enforce 模式实际拒绝次数（按资源与原因打点）；</li>
 *   <li>{@code kubo.datapermission.shadow} —— 观察模式下"本应过滤/本应脱敏"的次数；</li>
 *   <li>{@code kubo.datapermission.binding} —— 绑定异常（资源未注册、归属列缺失等），
 *       这类计数在 enforce 之前<b>必须归零</b>，否则"已防护"是假绿。</li>
 * </ul>
 *
 * <p>{@link MeterRegistry} 用 {@link ObjectProvider} 注入：测试上下文可能没有 actuator，
 * 缺 Bean 时退化为仅日志，不影响业务链路。</p>
 *
 * @author Hamm.cn
 */
@Slf4j
@Component
public class DataPermissionAudit {

    /**
     * 拒绝/告警原因
     */
    public static final String REASON_UNREGISTERED = "unregistered-resource";
    public static final String REASON_NO_POLICY = "no-policy-default-deny";
    public static final String REASON_BINDING_INVALID = "attribution-column-missing";
    public static final String REASON_PARSE_FAILED = "condition-parse-failed";
    public static final String REASON_WRITE_DENIED = "write-without-policy";

    /**
     * 写侧行级：既存行不在当前主体的数据范围内（改/删被拒）
     */
    public static final String REASON_ROW_NOT_WRITABLE = "row-not-writable";
    public static final String REASON_MASKED = "field-masked";
    public static final String REASON_PLAINTEXT_ACCESSED = "plaintext-accessed";

    /**
     * 归属迁移被拒（无 {@code system:data-permission:ownership} 权限）
     */
    public static final String REASON_OWNERSHIP_DENIED = "ownership-denied";

    /**
     * 归属迁移已执行（特权操作，落库审计）
     */
    public static final String REASON_OWNERSHIP_MIGRATED = "ownership-migrated";

    private final ObjectProvider<MeterRegistry> registryProvider;
    private final DataPermissionProperties properties;
    private final ObjectProvider<DataPermissionDenyLogRecorder> denyLogRecorderProvider;

    /**
     * 测试上下文可能没有 actuator；用一个进程内注册表兜底，保证打点逻辑本身不出错
     */
    private final MeterRegistry fallbackRegistry = new SimpleMeterRegistry();
    private final Map<String, Long> lastBindingWarnAt = new ConcurrentHashMap<>();

    /**
     * 绑定告警最小间隔（毫秒），避免每请求刷屏
     */
    private static final long BINDING_WARN_INTERVAL_MS = 60_000L;

    public DataPermissionAudit(ObjectProvider<MeterRegistry> registryProvider,
                               DataPermissionProperties properties,
                               ObjectProvider<DataPermissionDenyLogRecorder> denyLogRecorderProvider) {
        this.registryProvider = registryProvider;
        this.properties = properties;
        this.denyLogRecorderProvider = denyLogRecorderProvider;
    }

    /**
     * 记录 enforce 拒绝
     *
     * <p>除了计数与 WARN，还异步落一条 {@code data_permission_deny_log}（见 {@link DataPermissionDenyLogRecorder}）：
     * 事后追认「谁在何时、因何、在哪个档位下被拒」。落库是尽力而为 —— 失败或缺 Bean 只影响证据，
     * 绝不改变拒绝结果（这里先记后抛的顺序不变）。</p>
     */
    public void deny(String resourceKey, String reason, String detail) {
        counter("kubo.datapermission.deny", resourceKey, reason).increment();
        log.warn("[data-permission][deny] resource={} reason={} {}", resourceKey, reason, detail == null ? "" : detail);
        DataPermissionDenyLogRecorder recorder = denyLogRecorderProvider.getIfAvailable();
        if (recorder != null) {
            recorder.record(resourceKey, reason, detail);
        }
    }

    /**
     * 记录观察模式下"本应发生"的动作；不改变结果
     */
    public void shadow(String resourceKey, String reason, String detail) {
        counter("kubo.datapermission.shadow", resourceKey, reason).increment();
        if (properties.isShadowDiffLog()) {
            log.info("[data-permission][shadow] resource={} reason={} {}", resourceKey, reason, detail == null ? "" : detail);
        }
    }

    /**
     * 记录绑定异常（资源未注册 / 归属列不存在）
     *
     * <p>同一资源在 {@value #BINDING_WARN_INTERVAL_MS} ms 内只打一条日志，指标照常累加。</p>
     */
    public void binding(String resourceKey, String reason, String detail) {
        counter("kubo.datapermission.binding", resourceKey, reason).increment();
        long now = System.currentTimeMillis();
        Long last = lastBindingWarnAt.get(resourceKey);
        if (last == null || now - last > BINDING_WARN_INTERVAL_MS) {
            lastBindingWarnAt.put(resourceKey, now);
            log.error("[data-permission][binding] resource={} reason={} {} —— "
                    + "该行级/列级条件当前**未真正生效**，enforce 前必须清零", resourceKey, reason, detail == null ? "" : detail);
        }
    }

    /**
     * 权限判定耗时（用于验证"缓存命中后开销可忽略"这条承诺）
     */
    public void recordDecisionNanos(String resourceKey, long nanos) {
        Timer.builder("kubo.datapermission.decision")
                .tag("resource", resourceKey)
                .register(registryProvider.getIfAvailable() != null ? registryProvider.getIfAvailable() : fallbackRegistry)
                .record(nanos, TimeUnit.NANOSECONDS);
    }

    private Counter counter(String name, String resourceKey, String reason) {
        MeterRegistry registry = registryProvider.getIfAvailable();
        return Counter.builder(name)
                .tag("resource", resourceKey == null ? "unknown" : resourceKey)
                .tag("reason", reason)
                .register(registry != null ? registry : fallbackRegistry);
    }
}
