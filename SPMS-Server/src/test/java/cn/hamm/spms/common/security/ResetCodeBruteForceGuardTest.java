package cn.hamm.spms.common.security;

import cn.hamm.spms.common.redis.RedisHelper;
import cn.hamm.spms.framework.error.BusinessException;
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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 密码重置验证码防暴力破解守卫的契约测试。
 *
 * <p>钉四个不变量（口径同 {@link LoginBruteForceGuardTest}）：锁定期内拒绝重置（抛业务异常）；
 * 失败达阈值写锁定 key 并重置计数；未达阈值只计数不锁定；重置成功清零账号与 IP 的失败计数。
 * 外加窗口 TTL 与验证码生命周期（5 分钟）对齐的钉子——改窗口的人必须同时来改这条断言。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("ResetCodeBruteForceGuard · 重置验证码防暴力破解")
@ExtendWith(MockitoExtension.class)
class ResetCodeBruteForceGuardTest {

    @Mock
    private RedisHelper redisHelper;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    /** 真实解析器，仅 127.0.0.1 是可信代理（其余对端不采信转发头），口径同登录守卫测试 */
    @Spy
    private ClientIpResolver clientIpResolver = new ClientIpResolver("127.0.0.1");

    @InjectMocks
    private ResetCodeBruteForceGuard guard;

    @BeforeEach
    void setUp() {
        // 生产路径：Redis 可用（getRedisTemplate 非 null），opsForValue 委托给 mock。
        // lenient：部分用例不走 opsForValue，严格模式下会误报 UnnecessaryStubbing。
        org.mockito.Mockito.lenient().when(redisHelper.getRedisTemplate()).thenReturn(redisTemplate);
        org.mockito.Mockito.lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("锁定期内拒绝重置（抛验证码错误次数过多）")
    void assertNotLockedRejectsWhenLocked() {
        given(redisHelper.hasKey("kubo:reset:lock:13800138000")).willReturn(true);

        assertThrows(BusinessException.class, () -> guard.assertNotLocked("13800138000", "1.2.3.4"));
    }

    @Test
    @DisplayName("失败达阈值：写锁定 key 并重置计数")
    void recordFailureLocksWhenThresholdReached() {
        given(valueOperations.increment("kubo:reset:fail:13800138000")).willReturn(5L);

        guard.recordFailure("13800138000", "1.2.3.4");

        // RedisHelper#set(String, Object, long) 的 TTL 形参是 long，int 匹配器匹配不上
        verify(redisHelper).set(eq("kubo:reset:lock:13800138000"), any(), anyLong());
        verify(redisHelper).delete("kubo:reset:fail:13800138000");
    }

    @Test
    @DisplayName("未达阈值：只计数不锁定")
    void recordFailureDoesNotLockBelowThreshold() {
        given(valueOperations.increment("kubo:reset:fail:13800138000")).willReturn(4L);

        guard.recordFailure("13800138000", "1.2.3.4");

        verify(redisHelper, never()).set(anyString(), any(), anyLong());
    }

    @Test
    @DisplayName("重置成功：清零账号与 IP 的失败计数与锁定态")
    void clearResetsCounters() {
        guard.clear("13800138000", "1.2.3.4");

        verify(redisHelper).delete("kubo:reset:fail:13800138000");
        verify(redisHelper).delete("kubo:reset:lock:13800138000");
        verify(redisHelper).delete("kubo:reset:ip:fail:1.2.3.4");
        verify(redisHelper).delete("kubo:reset:ip:lock:1.2.3.4");
    }

    @Test
    @DisplayName("首次失败：计数窗口 TTL = 验证码 TTL（5 分钟），一枚码最多挨 5 次尝试")
    void recordFailureSetsTtlMatchedToCodeLifetime() {
        given(valueOperations.increment("kubo:reset:fail:13800138000")).willReturn(1L);

        guard.recordFailure("13800138000", "1.2.3.4");

        verify(redisTemplate).expire(eq("kubo:reset:fail:13800138000"), eq(5 * 60L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("对端为可信代理：解析 X-Forwarded-For 取第一个 IP")
    void resolveClientIpTakesFirstForwarded() {
        HttpServletRequest request = org.mockito.Mockito.mock(HttpServletRequest.class);
        given(request.getRemoteAddr()).willReturn("127.0.0.1");
        given(request.getHeader("X-Forwarded-For")).willReturn("1.2.3.4, 5.6.7.8");

        assertEquals("1.2.3.4", guard.resolveClientIp(request));
    }

    @Test
    @DisplayName("对端非可信代理时伪造 XFF 无效，取直连地址")
    void resolveClientIpIgnoresSpoofedForwardedFromUntrustedPeer() {
        HttpServletRequest request = org.mockito.Mockito.mock(HttpServletRequest.class);
        given(request.getRemoteAddr()).willReturn("203.0.113.9");
        // lenient：修复后这些头对非可信对端根本不会被读取，保留存根仅用于证明"即使伪造了也无效"
        org.mockito.Mockito.lenient().when(request.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4, 5.6.7.8");
        org.mockito.Mockito.lenient().when(request.getHeader("X-Real-IP")).thenReturn("8.8.8.8");

        assertEquals("203.0.113.9", guard.resolveClientIp(request));
    }
}
