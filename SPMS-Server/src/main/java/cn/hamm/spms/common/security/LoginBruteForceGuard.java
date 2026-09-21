package cn.hamm.spms.common.security;

import cn.hamm.spms.common.redis.RedisHelper;
import jakarta.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

import static cn.hamm.spms.common.exception.CustomError.LOGIN_ATTEMPT_TOO_MANY;

/**
 * <h1>登录防暴力破解守卫</h1>
 *
 * <p>生产级安全能力（本会话「生产级能力补全」第 1 项）：对登录失败做「账号 + IP」双维度计数，
 * 达到阈值后锁定一段时间，堵住撞库（针对单账号暴力破解）与扫号（单 IP 打多账号）两条攻击路径。
 * 复用项目既有的 Redis 原语（{@code increment} + {@code expire} + {@code set(key,val,ttl)}），
 * 无新增依赖。</p>
 *
 * <h2>维度与阈值</h2>
 * <ul>
 *   <li><b>账号维度</b>（核心，防撞库）：同一账号 {@value #MAX_FAIL_ATTEMPTS} 次失败 → 锁定
 *       {@value #LOCK_SECONDS} 秒；</li>
 *   <li><b>IP 维度</b>（兜底，防扫号）：同一 IP {@value #IP_MAX_FAIL_ATTEMPTS} 次失败 → 锁定。
 *       阈值刻意设高 —— 企业内网常经 NAT 共享出口 IP，纯 IP 限流阈值过低会误伤同网段正常用户。</li>
 * </ul>
 *
 * <h2>Redis 不可用降级</h2>
 * <p>单机调试未配 Redis 时 {@link RedisHelper#getRedisTemplate()} 返回 {@code null}，此时全部方法
 * 短路（不锁定、不计数、不清零），保持改造前「无防爆破」行为。生产环境 Redis 是数据权限 / 缓存的
 * 硬依赖，守卫必然生效；此分支仅服务本地无 Redis 的调试场景（与 {@code OrderService} 报工并发锁
 * 的降级口径一致）。</p>
 *
 * <h2>可配置化</h2>
 * <p>阈值当前为常量。如需按部署拓扑调整（公网环境可收紧 IP 阈值），将常量迁至
 * {@code @ConfigurationProperties} 即可，语义不变。</p>
 *
 * @author Hamm.cn
 */
@Component
public class LoginBruteForceGuard {
    /** 账号维度失败计数 key 前缀 */
    private static final String FAIL_KEY_PREFIX = "kubo:login:fail:";
    /** 账号维度锁定 key 前缀 */
    private static final String LOCK_KEY_PREFIX = "kubo:login:lock:";
    /** IP 维度失败计数 key 前缀 */
    private static final String IP_FAIL_KEY_PREFIX = "kubo:login:ip:fail:";
    /** IP 维度锁定 key 前缀 */
    private static final String IP_LOCK_KEY_PREFIX = "kubo:login:ip:lock:";

    /** 账号维度：允许的最大失败次数（窗口内） */
    private static final int MAX_FAIL_ATTEMPTS = 5;
    /** 失败计数窗口（秒）：窗口内失败累计到阈值即锁定 */
    private static final int FAIL_WINDOW_SECONDS = 15 * 60;
    /** 锁定时长（秒） */
    private static final int LOCK_SECONDS = 15 * 60;
    /** IP 维度：允许的最大失败次数（高阈值，防 NAT 误伤） */
    private static final int IP_MAX_FAIL_ATTEMPTS = 50;

    @Autowired
    private RedisHelper redisHelper;

    @Autowired
    private ClientIpResolver clientIpResolver;

    /**
     * 从请求解析客户端真实 IP（阶段 F-1：收敛到 {@link ClientIpResolver}，
     * 仅当直连对端在 {@code kubo.security.trusted-proxies} 白名单内才采信
     * X-Forwarded-For/X-Real-IP，防止伪造头绕过 IP 维度锁定）。
     *
     * @param request HTTP 请求
     * @return 客户端 IP，取不到返回 {@code null}
     */
    @Nullable
    public String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String ip = clientIpResolver.resolve(request);
        return StringUtils.hasText(ip) ? ip : null;
    }

    /**
     * 登录前校验：账号或 IP 处于锁定期则直接抛出「登录失败次数过多」异常（不进入失败计数）。
     *
     * @param account 登录账号（邮箱 / 手机号），为空则跳过（空账号由参数校验拦截，不构成爆破）
     * @param ip      客户端 IP，为空则跳过 IP 维度
     */
    public void assertNotLocked(@Nullable String account, @Nullable String ip) {
        if (redisUnavailable() || !StringUtils.hasText(account)) {
            return;
        }
        if (redisHelper.hasKey(LOCK_KEY_PREFIX + account)) {
            LOGIN_ATTEMPT_TOO_MANY.show();
        }
        if (StringUtils.hasText(ip) && redisHelper.hasKey(IP_LOCK_KEY_PREFIX + ip)) {
            LOGIN_ATTEMPT_TOO_MANY.show();
        }
    }

    /**
     * 登录失败后计数。达到阈值则写锁定 key（带 TTL）并重置计数窗口。
     *
     * @param account 登录账号
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
        }

        if (StringUtils.hasText(ip)) {
            Long ipFail = redisTemplate.opsForValue().increment(IP_FAIL_KEY_PREFIX + ip);
            if (ipFail != null && ipFail == 1L) {
                redisTemplate.expire(IP_FAIL_KEY_PREFIX + ip, FAIL_WINDOW_SECONDS, TimeUnit.SECONDS);
            }
            if (ipFail != null && ipFail >= IP_MAX_FAIL_ATTEMPTS) {
                redisHelper.set(IP_LOCK_KEY_PREFIX + ip, "1", LOCK_SECONDS);
                redisHelper.delete(IP_FAIL_KEY_PREFIX + ip);
            }
        }
    }

    /**
     * 登录成功后清零该账号与 IP 的失败计数与锁定态。
     *
     * @param account 登录账号
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
