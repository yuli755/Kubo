package cn.hamm.spms.common.security;

import cn.hamm.spms.common.notification.NotificationHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * <h1>守卫 Redis 降级放行上报器（R45 决策点 D1 的落地件）</h1>
 *
 * <p><b>它补的是哪个洞</b>：两守卫的「Redis 不可用降级放行」此前只覆盖<em>未配置</em>
 * {@code spring.data.redis.host} 的场景（template 为 null，方法短路）。生产 redis
 * <em>运行时</em>宕机时 template 非 null，{@code hasKey}/{@code increment} 抛
 * {@code RedisConnectionFailureException}，无人捕获裸奔成 500——用户重置密码收到 500，
 * 与文档声明的「可用性优先」相反。守卫侧捕获 {@code DataAccessException} 后调用本方法：
 * 记 WARN 日志 + webhook 告警（节流）→ 放行。</p>
 *
 * <p><b>为什么不 fail-closed</b>：redis 抖动会让所有用户无法登录/重置密码，比暂时打开
 * 防爆破闸更糟（验证码爆破还有发码侧配额闸门在）。风险显式存案于报告 §21，与被否决的
 * fail-closed 一并记录。</p>
 *
 * <p><b>节流语义</b>：WARN 日志每次都打（排障要看全貌）；webhook 按
 * {@code kubo.security.brute-force.degrade-alert-interval-seconds}（默认 60s）CAS 单点节流，
 * 并发下最多一条穿越——可接受。告警自身抛错只记日志绝不上抛：降级路径上再炸一次是本末倒置。</p>
 *
 * @author Hamm.cn
 */
@Slf4j
@Component
public class RedisDegradationReporter {

    /** 上一次 webhook 告警的时间戳（毫秒），CAS 比较基准 */
    private final AtomicLong lastAlertAt = new AtomicLong(0L);

    @Autowired
    private SecurityGuardProperties properties;

    @Autowired(required = false)
    private NotificationHelper notificationHelper;

    /**
     * 上报一次守卫 Redis 降级放行。本方法吞掉一切异常，保证调用方的放行结论。
     *
     * @param guard 守卫名（{@code login} / {@code reset}），进日志与告警标题
     * @param op    守卫内操作名（{@code assertNotLocked} / {@code recordFailure} / {@code clear}）
     * @param cause 捕获到的数据访问异常
     */
    public void report(String guard, String op, Exception cause) {
        log.warn("防爆破守卫 [{}] 操作 [{}] Redis 访问失败，本次降级放行（防爆破闸暂时打开）: {}",
                guard, op, cause.getMessage());
        long intervalMillis = properties.getDegradeAlertIntervalSeconds() * 1000L;
        long now = System.currentTimeMillis();
        long last = lastAlertAt.get();
        if (now - last < intervalMillis || !lastAlertAt.compareAndSet(last, now)) {
            // 节流窗口内，或并发下别的线程已抢到本次告警位——只留日志
            return;
        }
        if (notificationHelper == null) {
            return;
        }
        try {
            notificationHelper.sendWarning("安全风控告警：Redis 故障，防爆破守卫降级放行",
                    String.format("守卫 [%s] 操作 [%s] Redis 访问失败（%s），已按可用性优先策略放行；"
                                    + "告警按 %d 秒节流，期间同类降级只记 WARN 日志。请尽快检查 Redis。",
                            guard, op, cause.getMessage(), properties.getDegradeAlertIntervalSeconds()));
        } catch (Exception e) {
            log.warn("降级告警发送失败（忽略，不影响放行结论）: {}", e.getMessage());
        }
    }
}
