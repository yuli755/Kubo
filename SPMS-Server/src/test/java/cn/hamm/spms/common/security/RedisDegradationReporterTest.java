package cn.hamm.spms.common.security;

import cn.hamm.spms.common.notification.NotificationHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * <h1>RedisDegradationReporter 契约测试（R45/D1 降级上报）</h1>
 *
 * <p>钉四条底线：首次上报必发 webhook；节流窗口内同类降级只发一条（告警风暴防线）；
 * notificationHelper 缺席或告警自身抛错都不上抛（降级路径上绝不允许再炸一次）。</p>
 *
 * <p>不走 {@code @InjectMocks}：被测件两个 {@code @Autowired} 字段直接
 * {@code ReflectionTestUtils.setField} 装配，properties 用真实绑定器（默认 60s 节流），
 * 时序断言只依赖「两次调用间隔 ≪ 60s」这一条，无 flake。</p>
 */
@DisplayName("RedisDegradationReporter · 降级上报与节流")
@ExtendWith(MockitoExtension.class)
class RedisDegradationReporterTest {

    @Mock
    private NotificationHelper notificationHelper;

    private SecurityGuardProperties properties;

    private RedisDegradationReporter reporter;

    @BeforeEach
    void setUp() {
        properties = new SecurityGuardProperties();
        reporter = new RedisDegradationReporter();
        ReflectionTestUtils.setField(reporter, "properties", properties);
        ReflectionTestUtils.setField(reporter, "notificationHelper", notificationHelper);
    }

    private RedisConnectionFailureException redisDown() {
        return new RedisConnectionFailureException("redis down");
    }

    @Test
    @DisplayName("首次降级：WARN 之外必发一条 webhook 告警，内容含守卫名与操作名")
    void firstReportSendsWebhook() {
        reporter.report("login", "assertNotLocked", redisDown());

        verify(notificationHelper).sendWarning(anyString(), contains("login"));
    }

    @Test
    @DisplayName("节流窗口内连续降级：webhook 只发一条（告警风暴防线）")
    void throttlesRepeatedReports() {
        reporter.report("reset", "recordFailure", redisDown());
        reporter.report("reset", "recordFailure", redisDown());
        reporter.report("login", "clear", redisDown());

        verify(notificationHelper, times(1)).sendWarning(anyString(), anyString());
    }

    @Test
    @DisplayName("节流窗口过后：恢复上报")
    void reportsAgainAfterIntervalElapsed() {
        reporter.report("login", "recordFailure", redisDown());
        // 把上次告警时间拨回远古 = 节流窗口已过（不真等 60s）
        ReflectionTestUtils.setField(reporter, "lastAlertAt", new AtomicLong(0L));
        reporter.report("login", "recordFailure", redisDown());

        verify(notificationHelper, times(2)).sendWarning(anyString(), anyString());
    }

    @Test
    @DisplayName("notificationHelper 缺席（未配通知通道）：静默降级，绝不抛错")
    void survivesMissingNotificationHelper() {
        ReflectionTestUtils.setField(reporter, "notificationHelper", null);

        assertDoesNotThrow(() -> reporter.report("login", "assertNotLocked", redisDown()));
    }

    @Test
    @DisplayName("告警自身抛错被吞：降级路径上绝不允许再炸一次")
    void swallowsNotificationFailure() {
        doThrow(new RuntimeException("webhook down"))
                .when(notificationHelper).sendWarning(anyString(), anyString());

        assertDoesNotThrow(() -> reporter.report("reset", "clear", redisDown()));
    }

    @Test
    @DisplayName("节流间隔可配置：interval 改 0 后每次都发")
    void intervalConfigurable() {
        properties.setDegradeAlertIntervalSeconds(0);

        reporter.report("login", "recordFailure", redisDown());
        reporter.report("login", "recordFailure", redisDown());

        verify(notificationHelper, times(2)).sendWarning(anyString(), anyString());
    }

    @Test
    @DisplayName("负样本：节流命中时连 sendWarning 都不调用（而不是发了再丢）")
    void suppressedReportNeverCallsNotification() {
        reporter.report("login", "recordFailure", redisDown());
        reporter.report("login", "recordFailure", redisDown());

        verify(notificationHelper, times(1)).sendWarning(eq("安全风控告警：Redis 故障，防爆破守卫降级放行"), anyString());
        verify(notificationHelper, never()).sendWarning(anyString(), contains("second-noise"));
    }
}
