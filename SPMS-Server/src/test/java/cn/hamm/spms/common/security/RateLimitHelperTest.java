package cn.hamm.spms.common.security;

import cn.hamm.spms.framework.redis.RedisHelper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * <h1>RateLimitHelper 契约测试（固定窗口限流）</h1>
 *
 * <p>锁住三条底线：窗口内超限拒绝；Redis 不可用时降级放行（不误伤）；首次计数设过期（key 不泄漏）。</p>
 */
@DisplayName("RateLimitHelper · 固定窗口限流")
@ExtendWith(MockitoExtension.class)
class RateLimitHelperTest {

    @Mock
    private RedisHelper redisHelper;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private RateLimitHelper rateLimitHelper;

    /**
     * 阶段 F-1：限流 IP 判定已收敛到 ClientIpResolver（可信代理门控）。
     * spy 白名单仅 127.0.0.1，与下面用例的"可信对端才采信 XFF"语义对齐。
     */
    @Spy
    private ClientIpResolver clientIpResolver = new ClientIpResolver("127.0.0.1");

    @BeforeEach
    void setUp() {
        // lenient：降级放行类用例（Redis 不可用 / 取不到 IP）不会消费这两个存根，严格模式下会误报 UnnecessaryStubbing。
        org.mockito.Mockito.lenient().when(redisHelper.getRedisTemplate()).thenReturn(redisTemplate);
        org.mockito.Mockito.lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("窗口内未超限放行")
    void allowsWithinLimit() {
        given(valueOperations.increment("k:ip")).willReturn(5L);
        assertTrue(rateLimitHelper.allow("k:ip", 10, 60));
    }

    @Test
    @DisplayName("窗口内超限拒绝")
    void rejectsBeyondLimit() {
        given(valueOperations.increment("k:ip")).willReturn(11L);
        assertFalse(rateLimitHelper.allow("k:ip", 10, 60));
    }

    @Test
    @DisplayName("首次计数（increment 返回 1）会设过期时间")
    void setsExpireOnFirstIncrement() {
        given(valueOperations.increment("k:ip")).willReturn(1L);
        rateLimitHelper.allow("k:ip", 10, 60);
        verify(redisTemplate).expire(eq("k:ip"), eq(60L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Redis 不可用时降级放行（不误伤）")
    void allowsWhenRedisDown() {
        given(redisHelper.getRedisTemplate()).willReturn(null);
        assertTrue(rateLimitHelper.allow("k:ip", 10, 60));
    }

    @Test
    @DisplayName("按 IP 限流：可信对端时 X-Forwarded-For 取首个 IP")
    void resolvesForwardedForIp() {
        HttpServletRequest request = org.mockito.Mockito.mock(HttpServletRequest.class);
        // 阶段 F-1：仅当直连对端可信（spy 白名单含 127.0.0.1）才采信 XFF
        given(request.getRemoteAddr()).willReturn("127.0.0.1");
        given(request.getHeader("X-Forwarded-For")).willReturn("203.0.113.7, 10.0.0.1");
        given(valueOperations.increment("kubo:api:rate:203.0.113.7")).willReturn(1L);

        assertTrue(rateLimitHelper.allowByIp(request, "kubo:api:rate:", 3000, 60));
        verify(valueOperations).increment("kubo:api:rate:203.0.113.7");
    }

    @Test
    @DisplayName("按 IP 限流：取不到 IP 时降级放行")
    void allowsWhenIpUnresolvable() {
        HttpServletRequest request = org.mockito.Mockito.mock(HttpServletRequest.class);
        given(request.getRemoteAddr()).willReturn(null);

        assertTrue(rateLimitHelper.allowByIp(request, "kubo:api:rate:", 3000, 60));
    }
}
