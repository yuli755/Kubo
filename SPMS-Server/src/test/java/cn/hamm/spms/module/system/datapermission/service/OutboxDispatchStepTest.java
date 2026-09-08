package cn.hamm.spms.module.system.datapermission.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * <h1>Outbox 投递状态迁移的纯逻辑用例</h1>
 *
 * <p>判据只有两句：成功 → DONE 记处理时间；失败 → 重试次数 +1、未达上限按退避排下次、达上限死信。
 * 重试阈值与退避走 {@link PermissionChangeOutboxBackoff}（单源），这里钉的是状态迁移。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("OutboxDispatchStep · 投递状态迁移")
class OutboxDispatchStepTest {

    private static final long NOW = 1_000_000L;

    @Test
    @DisplayName("投递成功 → DONE，记处理时间，重试次数不变")
    void successMarksDone() {
        OutboxDispatchStep.Result result = OutboxDispatchStep.markDone(NOW);

        assertEquals("DONE", result.status());
        assertEquals(NOW, result.processedAt());
        assertNull(result.retryCount());
        assertNull(result.nextRetryAt());
        assertNull(result.lastError());
    }

    @Test
    @DisplayName("首次失败 → 仍 PENDING，重试次数 1，下次重试按退避排")
    void firstFailureSchedulesRetry() {
        OutboxDispatchStep.Result result = OutboxDispatchStep.markFailed(0, "redis down", NOW);

        assertEquals("PENDING", result.status());
        assertEquals(1, result.retryCount());
        assertEquals(NOW + 2_000L, result.nextRetryAt());
        assertEquals("redis down", result.lastError());
    }

    @Test
    @DisplayName("重试耗尽（第 5 次失败后）→ 死信 DEAD，不再排程")
    void exhaustedRetriesGoDead() {
        OutboxDispatchStep.Result result = OutboxDispatchStep.markFailed(4, "still down", NOW);

        assertEquals("DEAD", result.status());
        assertEquals(5, result.retryCount());
        assertNull(result.nextRetryAt());
        assertEquals("still down", result.lastError());
    }

    @Test
    @DisplayName("超过上限继续失败 → 仍死信，不再排程")
    void overExhaustedStaysDead() {
        OutboxDispatchStep.Result result = OutboxDispatchStep.markFailed(9, "still down", NOW);

        assertEquals("DEAD", result.status());
        assertEquals(10, result.retryCount());
        assertNull(result.nextRetryAt());
    }
}
