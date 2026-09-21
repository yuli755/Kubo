package cn.hamm.spms.common.security;

import cn.hamm.spms.framework.error.BusinessException;
import cn.hamm.spms.common.redis.RedisHelper;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 登录防暴力破解守卫的契约测试。
 *
 * <p>钉四个不变量：锁定期内拒绝登录（抛业务异常）；失败达阈值写锁定 key 并重置计数；
 * 未达阈值只计数不锁定；登录成功清零账号与 IP 的失败计数。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("LoginBruteForceGuard · 登录防暴力破解")
@ExtendWith(MockitoExtension.class)
class LoginBruteForceGuardTest {

    @Mock
    private RedisHelper redisHelper;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    /** 阶段 F-1：真实解析器，仅 127.0.0.1 是可信代理（其余对端不采信转发头） */
    @Spy
    private ClientIpResolver clientIpResolver = new ClientIpResolver("127.0.0.1");

    @InjectMocks
    private LoginBruteForceGuard guard;

    @BeforeEach
    void setUp() {
        // 生产路径：Redis 可用（getRedisTemplate 非 null），opsForValue 委托给 mock。
        // lenient：部分用例（如 assertNotLocked / resolveClientIp）不走 opsForValue，严格模式下会误报 UnnecessaryStubbing。
        org.mockito.Mockito.lenient().when(redisHelper.getRedisTemplate()).thenReturn(redisTemplate);
        org.mockito.Mockito.lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("锁定期内拒绝登录（抛登录失败次数过多）")
    void assertNotLockedRejectsWhenLocked() {
        given(redisHelper.hasKey("kubo:login:lock:a@b.com")).willReturn(true);

        assertThrows(BusinessException.class, () -> guard.assertNotLocked("a@b.com", "1.2.3.4"));
    }

    @Test
    @DisplayName("失败达阈值：写锁定 key 并重置计数")
    void recordFailureLocksWhenThresholdReached() {
        given(valueOperations.increment("kubo:login:fail:a@b.com")).willReturn(5L);

        guard.recordFailure("a@b.com", "1.2.3.4");

        // RedisHelper#set(String, Object, long) 的 TTL 形参是 long，int 匹配器匹配不上
        verify(redisHelper).set(eq("kubo:login:lock:a@b.com"), any(), anyLong());
        verify(redisHelper).delete("kubo:login:fail:a@b.com");
    }

    @Test
    @DisplayName("未达阈值：只计数不锁定")
    void recordFailureDoesNotLockBelowThreshold() {
        given(valueOperations.increment("kubo:login:fail:a@b.com")).willReturn(3L);

        guard.recordFailure("a@b.com", "1.2.3.4");

        verify(redisHelper, never()).set(anyString(), any(), anyLong());
    }

    @Test
    @DisplayName("登录成功：清零账号与 IP 的失败计数与锁定态")
    void clearResetsCounters() {
        guard.clear("a@b.com", "1.2.3.4");

        verify(redisHelper).delete("kubo:login:fail:a@b.com");
        verify(redisHelper).delete("kubo:login:lock:a@b.com");
        verify(redisHelper).delete("kubo:login:ip:fail:1.2.3.4");
        verify(redisHelper).delete("kubo:login:ip:lock:1.2.3.4");
    }

    @Test
    @DisplayName("首次失败：给计数窗口设 TTL")
    void recordFailureSetsTtlOnFirstFailure() {
        given(valueOperations.increment("kubo:login:fail:a@b.com")).willReturn(1L);

        guard.recordFailure("a@b.com", "1.2.3.4");

        verify(redisTemplate).expire(eq("kubo:login:fail:a@b.com"), eq(15 * 60L), eq(TimeUnit.SECONDS));
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
    @DisplayName("阶段 F-1：对端非可信代理时伪造 XFF 无效，取直连地址")
    void resolveClientIpIgnoresSpoofedForwardedFromUntrustedPeer() {
        HttpServletRequest request = org.mockito.Mockito.mock(HttpServletRequest.class);
        given(request.getRemoteAddr()).willReturn("203.0.113.9");
        // lenient：修复后这些头对非可信对端根本不会被读取，保留存根仅用于证明"即使伪造了也无效"
        org.mockito.Mockito.lenient().when(request.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4, 5.6.7.8");
        org.mockito.Mockito.lenient().when(request.getHeader("X-Real-IP")).thenReturn("8.8.8.8");

        assertEquals("203.0.113.9", guard.resolveClientIp(request));
    }
}
