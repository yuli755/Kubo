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
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
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

    /** R45：真实绑定器——默认值即参数化前的常量，既有用例断言因此逐位不变 */
    @Spy
    private SecurityGuardProperties properties = new SecurityGuardProperties();

    /** R45：降级上报 mock（D1：Redis 运行时故障 → 放行 + 上报） */
    @Mock
    private RedisDegradationReporter degradationReporter;

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

    @Test
    @DisplayName("R45/D1：assertNotLocked 遇 Redis 运行时故障 → 降级放行并上报")
    void assertNotLockedDegradesOnRuntimeFailure() {
        given(redisHelper.hasKey("kubo:login:lock:a@b.com"))
                .willThrow(new RedisConnectionFailureException("redis down"));

        assertDoesNotThrow(() -> guard.assertNotLocked("a@b.com", "1.2.3.4"));

        verify(degradationReporter).report(eq("login"), eq("assertNotLocked"), any());
    }

    @Test
    @DisplayName("R45：锁定期业务异常不被 catch 越界吞掉（负样本）")
    void assertNotLockedStillThrowsBusinessException() {
        given(redisHelper.hasKey("kubo:login:lock:a@b.com")).willReturn(true);

        assertThrows(BusinessException.class, () -> guard.assertNotLocked("a@b.com", "1.2.3.4"));
        verify(degradationReporter, never()).report(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("R45/D1：recordFailure 遇 Redis 运行时故障 → 放弃本次计数并上报，不上抛打挂登录流程")
    void recordFailureDegradesOnRuntimeFailure() {
        given(valueOperations.increment("kubo:login:fail:a@b.com"))
                .willThrow(new RedisConnectionFailureException("redis down"));

        assertDoesNotThrow(() -> guard.recordFailure("a@b.com", "1.2.3.4"));

        verify(degradationReporter).report(eq("login"), eq("recordFailure"), any());
        verify(redisHelper, never()).set(anyString(), any(), anyLong());
    }

    @Test
    @DisplayName("R45/D1：clear 遇 Redis 运行时故障 → 只上报，已成功的登录响应不能变 500")
    void clearDegradesOnRuntimeFailure() {
        doThrow(new RedisConnectionFailureException("redis down"))
                .when(redisHelper).delete("kubo:login:fail:a@b.com");

        assertDoesNotThrow(() -> guard.clear("a@b.com", "1.2.3.4"));

        verify(degradationReporter).report(eq("login"), eq("clear"), any());
    }

    @Test
    @DisplayName("R45：阈值取自配置——max-fail-attempts 改 2 后第 2 次失败即锁定")
    void thresholdComesFromProperties() {
        properties.getLogin().setMaxFailAttempts(2);
        given(valueOperations.increment("kubo:login:fail:a@b.com")).willReturn(2L);

        guard.recordFailure("a@b.com", null);

        verify(redisHelper).set(eq("kubo:login:lock:a@b.com"), any(), anyLong());
    }
}
