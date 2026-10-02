package cn.hamm.spms.common.security;

import cn.hamm.spms.common.notification.NotificationHelper;
import cn.hamm.spms.common.redis.RedisHelper;
import jakarta.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
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
 *   <li><b>账号维度</b>（核心，防撞库）：同一手机号/邮箱 {@value #MAX_FAIL_ATTEMPTS} 次验码失败
 *       → 锁定 {@value #LOCK_SECONDS} 秒；</li>
 *   <li><b>IP 维度</b>（兜底，防扫号）：同一 IP {@value #IP_MAX_FAIL_ATTEMPTS} 次失败 → 锁定。
 *       阈值刻意设高（口径同登录守卫：企业 NAT 共享出口，纯 IP 低阈值会误伤同网段用户）。</li>
 * </ul>
 *
 * <h2>窗口与验证码生命周期对齐</h2>
 * <p>失败计数窗口取 {@value #FAIL_WINDOW_SECONDS} 秒 = 验证码 TTL（
 * {@code UserService.CACHE_CODE_EXPIRE_SECOND}）：计数窗口覆盖一枚码的完整生命周期，
 * 一枚码从生到死最多挨 {@value #MAX_FAIL_ATTEMPTS} 次尝试；锁定时长刻意长于窗口，
 * 确保「换一枚新码接着猜」也要先等锁到期。</p>
 *
 * <h2>Redis 不可用降级</h2>
 * <p>与登录守卫同口径：本地调试未配 Redis 时 {@link RedisHelper#getRedisTemplate()} 返回
 * {@code null}，全部方法短路（不锁定、不计数、不清零），保持改造前行为；生产 Redis 是硬依赖，
 * 守卫必然生效。</p>
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

    /** 账号维度：允许的最大验码失败次数（窗口内） */
    private static final int MAX_FAIL_ATTEMPTS = 5;
    /** 失败计数窗口（秒）：与验证码 TTL（5 分钟）对齐，一枚码的一生最多挨 MAX 次尝试 */
    private static final int FAIL_WINDOW_SECONDS = 5 * 60;
    /** 锁定时长（秒）：刻意长于计数窗口，换码续猜也要先等锁到期 */
    private static final int LOCK_SECONDS = 15 * 60;
    /** IP 维度：允许的最大失败次数（高阈值，防 NAT 误伤，口径同登录守卫） */
    private static final int IP_MAX_FAIL_ATTEMPTS = 50;

    @Autowired
    private RedisHelper redisHelper;

    @Autowired
    private ClientIpResolver clientIpResolver;

    @Autowired(required = false)
    private NotificationHelper notificationHelper;

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
     * @param account 重置目标账号（手机号 / 邮箱），为空则跳过（空账号由参数校验拦截，不构成爆破）
     * @param ip      客户端 IP，为空则跳过 IP 维度
     */
    public void assertNotLocked(@Nullable String account, @Nullable String ip) {
        if (redisUnavailable() || !StringUtils.hasText(account)) {
            return;
        }
        if (redisHelper.hasKey(LOCK_KEY_PREFIX + account)) {
            RESET_ATTEMPT_TOO_MANY.show();
        }
        if (StringUtils.hasText(ip) && redisHelper.hasKey(IP_LOCK_KEY_PREFIX + ip)) {
            RESET_ATTEMPT_TOO_MANY.show();
        }
    }

    /**
     * 验码失败后计数。达到阈值则写锁定 key（带 TTL）并重置计数窗口。
     *
     * @param account 重置目标账号
     * @param ip      客户端 IP
     */
    public void recordFailure(@Nullable String account, @Nullable String ip) {
        if (redisUnavailable() || !StringUtils.hasText(account)) {
            return;
        }
        RedisTemplate<String, Object> redisTemplate = redisHelper.getRedisTemplate();

        Long accountFail = redisTemplate.opsForValue().increment(FAIL_KEY_PREFIX + account);
        if (accountFail != null && accountFail == 1L) {
            // 首次失败：给计数窗口设 TTL，窗口过后自动清零（不设则 key 永不过期）
            redisTemplate.expire(FAIL_KEY_PREFIX + account, FAIL_WINDOW_SECONDS, TimeUnit.SECONDS);
        }
        if (accountFail != null && accountFail >= MAX_FAIL_ATTEMPTS) {
            redisHelper.set(LOCK_KEY_PREFIX + account, "1", LOCK_SECONDS);
            redisHelper.delete(FAIL_KEY_PREFIX + account);
            if (notificationHelper != null) {
                notificationHelper.sendWarning("安全风控告警：密码重置防爆破锁定",
                        String.format("账号 [%s] 重置验证码连续错误达到 %d 次，已被锁定 %d 秒", account, MAX_FAIL_ATTEMPTS, LOCK_SECONDS));
            }
        }

        if (StringUtils.hasText(ip)) {
            Long ipFail = redisTemplate.opsForValue().increment(IP_FAIL_KEY_PREFIX + ip);
            if (ipFail != null && ipFail == 1L) {
                redisTemplate.expire(IP_FAIL_KEY_PREFIX + ip, FAIL_WINDOW_SECONDS, TimeUnit.SECONDS);
            }
            if (ipFail != null && ipFail >= IP_MAX_FAIL_ATTEMPTS) {
                redisHelper.set(IP_LOCK_KEY_PREFIX + ip, "1", LOCK_SECONDS);
                redisHelper.delete(IP_FAIL_KEY_PREFIX + ip);
                if (notificationHelper != null) {
                    notificationHelper.sendWarning("安全风控告警：IP重置防爆破锁定",
                            String.format("客户端 IP [%s] 重置验码失败达到 %d 次，已被锁定 %d 秒", ip, IP_MAX_FAIL_ATTEMPTS, LOCK_SECONDS));
                }
            }
        }
    }

    /**
     * 重置成功后清零该账号与 IP 的失败计数与锁定态。
     *
     * @param account 重置目标账号
     * @param ip      客户端 IP
     */
    public void clear(@Nullable String account, @Nullable String ip) {
        if (redisUnavailable()) {
            return;
        }
        if (StringUtils.hasText(account)) {
            redisHelper.delete(FAIL_KEY_PREFIX + account);
            redisHelper.delete(LOCK_KEY_PREFIX + account);
        }
        if (StringUtils.hasText(ip)) {
            redisHelper.delete(IP_FAIL_KEY_PREFIX + ip);
            redisHelper.delete(IP_LOCK_KEY_PREFIX + ip);
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
