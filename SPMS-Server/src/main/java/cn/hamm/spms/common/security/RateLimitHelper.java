package cn.hamm.spms.common.security;

import cn.hamm.spms.common.redis.RedisHelper;
import jakarta.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

/**
 * <h1>固定窗口限流组件（Redis 计数器）</h1>
 *
 * <p>统一「按 key 计数 + 过期时间」的固定窗口限流原语，供<b>未登录可访问的公开端点</b>做
 * 滥用面防护（防错误风暴 / 防扫描枚举 / 防 DoS）。此前登录防爆破（失败计数+锁定，语义不同）
 * 与错误上报（固定窗口）各自内联了一份 Redis 计数逻辑，本组件把「固定窗口」这份收敛为一处。</p>
 *
 * <h2>语义</h2>
 * <ul>
 *   <li>每个窗口（{@code windowSeconds}）内，同一 key 最多放行 {@code limit} 次，超过即拒绝；</li>
 *   <li>窗口内首次计数时顺带设过期（{@code increment} 返回 1 时 {@code expire}），key 不泄漏；</li>
 *   <li><b>Redis 不可用时放行</b>（与防爆破守卫同口径）：单机调试未配 Redis 不误伤，生产 Redis 是
 *       硬依赖、限流必然生效。</li>
 * </ul>
 *
 * <h2>阈值口径（重要）</h2>
 * <p>企业内网常经 NAT 共享出口 IP，纯 IP 限流阈值过低会误伤同网段正常终端（设备采集端、车间工位机）。
 * 因此<b>限流只做 DoS 防护、不做认证</b>：阈值按「明显异常的请求量」标定，而不是「正常业务量」。
 * 需要认证语义的端点应另加令牌/API-Key 校验，而不是靠限流。</p>
 *
 * @author Hamm.cn
 */
@Component
public class RateLimitHelper {

    @Autowired
    private RedisHelper redisHelper;

    @Autowired
    private ClientIpResolver clientIpResolver;

    /**
     * 判断 key 在窗口内是否允许放行。
     *
     * @param key           限流维度（如 IP、设备 UUID、账号）
     * @param limit         窗口内最大放行次数
     * @param windowSeconds 窗口时长（秒）
     * @return 允许放行返回 true；超限或（Redis 不可用）降级放行返回 true
     */
    public boolean allow(@NotNull String key, int limit, int windowSeconds) {
        RedisTemplate<String, Object> redisTemplate = redisHelper.getRedisTemplate();
        if (redisTemplate == null) {
            return true;
        }
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
        }
        return count == null || count <= limit;
    }

    /**
     * 按客户端 IP 限流（自动带 key 前缀拼 IP）。
     *
     * @param request       请求（取客户端 IP）
     * @param keyPrefix     key 前缀（如 {@code kubo:api:rate:}）
     * @param limit         窗口内最大放行次数
     * @param windowSeconds 窗口时长（秒）
     * @return 允许放行返回 true
     */
    public boolean allowByIp(@NotNull HttpServletRequest request,
                             @NotNull String keyPrefix,
                             int limit,
                             int windowSeconds) {
        // 阶段 F-1：IP 判定收敛到 ClientIpResolver（可信代理白名单门控），
        // 防止伪造 X-Forwarded-For 绕过公开端点限流
        String ip = clientIpResolver.resolve(request);
        if (!StringUtils.hasText(ip)) {
            // 取不到 IP 时无法按 IP 限流，放行（防误伤，同错误上报口径）
            return true;
        }
        return allow(keyPrefix + ip, limit, windowSeconds);
    }
}
