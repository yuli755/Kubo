package cn.hamm.spms.common.security;

import cn.hamm.spms.common.notification.NotificationHelper;
import cn.hamm.spms.common.redis.RedisHelper;
import jakarta.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

import static cn.hamm.spms.common.exception.CustomError.RESET_ATTEMPT_TOO_MANY;

/**
 * <h1>密码重置验证码防暴力破解守卫</h1>
 *
 * <p>堵住「忘记密码」链路上的验证码爆破面：{@code resetMyPassword} 对 6 位数字验证码做
 * 字符串比对，失败既不计数也不删码（删码只发生在重置成功后），在 5 分钟 TTL 内攻击者可
 * 对约 10<sup>6</sup> 的码空间无限次尝试。本守卫与 {@link LoginBruteForceGuard} 同族同形：
 * 「账号 + IP」双维度失败计数，达阈值锁定，复用既有 Redis 原语（{@code increment} +
 * {@code expire} + {@code set(key,val,ttl)}），无新增依赖。</p>
 *
 * <h2>为什么必须有失败上限（而不是靠发码限频）</h2>
 * <p>发码侧的 {@code EMAIL_SEND_BUSY}/{@code SMS_SEND_BUSY} 只限「发码频率」，不限「验码尝试」：
 * 码一旦发出，校验通道对同一枚码是敞开的。失败计数把尝试次数压到每窗口 5 次，
 * 15<sup>6</sup> ÷ 5 ≈ 爆破完码空间需数万个锁定周期，攻击在经济上不再可行。</p>
 *
 * <h2>维度与阈值</h2>
 * <ul>
 *   <li><b>账号维度</b>（核心，防撞库）：同一手机号/邮箱窗口内验码失败达
 *       {@code reset.max-fail-attempts} 次 → 锁定 {@code reset.lock-seconds} 秒；</li>
 *   <li><b>IP 维度</b>（兜底，防扫号）：同一 IP 失败达 {@code reset.ip-max-fail-attempts}
 *       次 → 锁定。阈值刻意设高（口径同登录守卫：企业 NAT 共享出口，纯 IP 低阈值会误伤同网段用户）。</li>
 * </ul>
 * <p>阈值来自 {@link SecurityGuardProperties}（{@code kubo.security.brute-force.reset.*}），
 * R45 参数化；<b>默认值即参数化前的常量值</b>，不配置 = 行为逐位不变。计数窗口默认与验证码
 * TTL（{@code UserService.CACHE_CODE_EXPIRE_SECOND}）对齐，一枚码从生到死最多挨
 * max-fail-attempts 次尝试；锁定时长刻意长于窗口，「换一枚新码接着猜」也要先等锁到期。</p>
 *
 * <h2>Redis 不可用降级（两种形态，口径不同）</h2>
 * <ul>
 *   <li><b>未配置 Redis</b>（本地调试）：{@link RedisHelper#getRedisTemplate()} 返回
 *       {@code null}，全部方法静默短路 —— 刻意的部署形态，告警只会是噪音；</li>
 *   <li><b>运行时故障</b>（R45/D1）：生产 Redis 配置在场但访问抛
 *       {@link DataAccessException} 时，记 WARN + 节流 webhook
 *       （{@link RedisDegradationReporter}）后<b>放行</b> —— 用户此刻正卡在改密码最后一步，
 *       收到 500 比爆破闸暂开几分钟伤害更大（发码侧配额闸门仍在）。风险存案见报告 §21。</li>
 * </ul>
 *
 * @author Hamm.cn
 */
@Component
public class ResetCodeBruteForceGuard {
    /** 账号维度失败计数 key 前缀 */
    private static final String FAIL_KEY_PREFIX = "kubo:reset:fail:";
    /** 账号维度锁定 key 前缀 */
    private static final String LOCK_KEY_PREFIX = "kubo:reset:lock:";
    /** IP 维度失败计数 key 前缀 */
    private static final String IP_FAIL_KEY_PREFIX = "kubo:reset:ip:fail:";
    /** IP 维度锁定 key 前缀 */
    private static final String IP_LOCK_KEY_PREFIX = "kubo:reset:ip:lock:";

    /** 降级告警的守卫名（进日志与 webhook 标题） */
    private static final String GUARD_NAME = "reset";

    @Autowired
    private RedisHelper redisHelper;

    @Autowired
    private ClientIpResolver clientIpResolver;

    @Autowired(required = false)
    private NotificationHelper notificationHelper;

    @Autowired
    private SecurityGuardProperties properties;

    @Autowired
    private RedisDegradationReporter degradationReporter;

    /**
     * 从请求解析客户端真实 IP（复用 {@link ClientIpResolver} 的可信代理白名单门控，
     * 防止伪造 X-Forwarded-For 绕过 IP 维度锁定）。
     *
     * @param request HTTP 请求
     * @return 客户端 IP，取不到返回 {@code null}
     */
    @Nullable
    public String resolveClientIp(@Nullable HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String ip = clientIpResolver.resolve(request);
        return StringUtils.hasText(ip) ? ip : null;
    }

    /**
     * 重置前校验：账号或 IP 处于锁定期则直接抛出「验证码错误次数过多」异常（不进入失败计数）。
     *
     * <p>Redis 运行时故障 → 记降级告警后放行（R45/D1），重置流程照常往下走。</p>
     *
     * @param account 重置目标账号（手机号 / 邮箱），为空则跳过（空账号由参数校验拦截，不构成爆破）
     * @param ip      客户端 IP，为空则跳过 IP 维度
     */
    public void assertNotLocked(@Nullable String account, @Nullable String ip) {
        if (redisUnavailable() || !StringUtils.hasText(account)) {
            return;
        }
        try {
            if (redisHelper.hasKey(LOCK_KEY_PREFIX + account)) {
                RESET_ATTEMPT_TOO_MANY.show();
            }
            if (StringUtils.hasText(ip) && redisHelper.hasKey(IP_LOCK_KEY_PREFIX + ip)) {
                RESET_ATTEMPT_TOO_MANY.show();
            }
        } catch (DataAccessException e) {
            // 业务异常（RESET_ATTEMPT_TOO_MANY）不是 DataAccessException，不会被本 catch 吞掉
            degradationReporter.report(GUARD_NAME, "assertNotLocked", e);
        }
    }

    /**
     * 验码失败后计数。达到阈值则写锁定 key（带 TTL）并重置计数窗口。
     *
     * <p>Redis 运行时故障 → 记降级告警后放弃本次计数（防爆破闸暂开，见类 javadoc）。</p>
     *
     * @param account 重置目标账号
     * @param ip      客户端 IP
     */
    public void recordFailure(@Nullable String account, @Nullable String ip) {
        if (redisUnavailable() || !StringUtils.hasText(account)) {
            return;
        }
        SecurityGuardProperties.Reset config = properties.getReset();
        try {
            RedisTemplate<String, Object> redisTemplate = redisHelper.getRedisTemplate();

            Long accountFail = redisTemplate.opsForValue().increment(FAIL_KEY_PREFIX + account);
            if (accountFail != null && accountFail == 1L) {
                // 首次失败：给计数窗口设 TTL，窗口过后自动清零（不设则 key 永不过期）
                redisTemplate.expire(FAIL_KEY_PREFIX + account, config.getFailWindowSeconds(), TimeUnit.SECONDS);
            }
            if (accountFail != null && accountFail >= config.getMaxFailAttempts()) {
                redisHelper.set(LOCK_KEY_PREFIX + account, "1", config.getLockSeconds());
                redisHelper.delete(FAIL_KEY_PREFIX + account);
                if (notificationHelper != null) {
                    notificationHelper.sendWarning("安全风控告警：密码重置防爆破锁定",
                            String.format("账号 [%s] 重置验证码连续错误达到 %d 次，已被锁定 %d 秒",
                                    account, config.getMaxFailAttempts(), config.getLockSeconds()));
                }
            }

            if (StringUtils.hasText(ip)) {
                Long ipFail = redisTemplate.opsForValue().increment(IP_FAIL_KEY_PREFIX + ip);
                if (ipFail != null && ipFail == 1L) {
                    redisTemplate.expire(IP_FAIL_KEY_PREFIX + ip, config.getFailWindowSeconds(), TimeUnit.SECONDS);
                }
                if (ipFail != null && ipFail >= config.getIpMaxFailAttempts()) {
                    redisHelper.set(IP_LOCK_KEY_PREFIX + ip, "1", config.getLockSeconds());
                    redisHelper.delete(IP_FAIL_KEY_PREFIX + ip);
                    if (notificationHelper != null) {
                        notificationHelper.sendWarning("安全风控告警：IP重置防爆破锁定",
                                String.format("客户端 IP [%s] 重置验码失败达到 %d 次，已被锁定 %d 秒",
                                        ip, config.getIpMaxFailAttempts(), config.getLockSeconds()));
                    }
                }
            }
        } catch (DataAccessException e) {
            degradationReporter.report(GUARD_NAME, "recordFailure", e);
        }
    }

    /**
     * 重置成功后清零该账号与 IP 的失败计数与锁定态。
     *
     * <p>清零失败（Redis 运行时故障）只记降级告警，绝不让已成功的重置响应变成 500。</p>
     *
     * @param account 重置目标账号
     * @param ip      客户端 IP
     */
    public void clear(@Nullable String account, @Nullable String ip) {
        if (redisUnavailable()) {
            return;
        }
        try {
            if (StringUtils.hasText(account)) {
                redisHelper.delete(FAIL_KEY_PREFIX + account);
                redisHelper.delete(LOCK_KEY_PREFIX + account);
            }
            if (StringUtils.hasText(ip)) {
                redisHelper.delete(IP_FAIL_KEY_PREFIX + ip);
                redisHelper.delete(IP_LOCK_KEY_PREFIX + ip);
            }
        } catch (DataAccessException e) {
            degradationReporter.report(GUARD_NAME, "clear", e);
        }
    }

    /**
     * Redis 是否可用（{@code RedisHelper} 注入后底层 template 可能为 null，本地无 Redis 调试场景）。
     *
     * @return {@code true} 表示 Redis 不可用，应跳过防爆破
     */
    private boolean redisUnavailable() {
        return redisHelper.getRedisTemplate() == null;
    }
}
