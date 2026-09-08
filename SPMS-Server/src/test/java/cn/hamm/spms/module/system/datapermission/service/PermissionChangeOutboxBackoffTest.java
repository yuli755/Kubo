package cn.hamm.spms.module.system.datapermission.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h1>Outbox 重试退避的纯逻辑用例</h1>
 *
 * <p>Dispatcher 还没接，规则先锁住：将来接 Dispatcher 只许调这里，不得再抄一份指数退避。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("PermissionChangeOutboxBackoff · 重试退避")
class PermissionChangeOutboxBackoffTest {

    @Test
    @DisplayName("重试次数未达上限不算死信，达到/超过算")
    void deadThresholdIsMaxRetry() {
        assertFalse(PermissionChangeOutboxBackoff.dead(0));
        assertFalse(PermissionChangeOutboxBackoff.dead(4));
        assertTrue(PermissionChangeOutboxBackoff.dead(5));
        assertTrue(PermissionChangeOutboxBackoff.dead(6));
    }

    @Test
    @DisplayName("退避按 2 的幂翻倍：1s → 2s → 4s → 8s → 16s")
    void backoffDoublesPerRetry() {
        long now = 1_000_000L;
        assertEquals(now + 1_000L, PermissionChangeOutboxBackoff.nextRetryAtMillis(0, now));
        assertEquals(now + 2_000L, PermissionChangeOutboxBackoff.nextRetryAtMillis(1, now));
        assertEquals(now + 4_000L, PermissionChangeOutboxBackoff.nextRetryAtMillis(2, now));
        assertEquals(now + 8_000L, PermissionChangeOutboxBackoff.nextRetryAtMillis(3, now));
        assertEquals(now + 16_000L, PermissionChangeOutboxBackoff.nextRetryAtMillis(4, now));
    }

    @Test
    @DisplayName("退避封顶 60s，不因重试次数继续增长而无限放大")
    void backoffIsCapped() {
        long now = 1_000_000L;
        assertEquals(now + 60_000L, PermissionChangeOutboxBackoff.nextRetryAtMillis(10, now));
        assertEquals(now + 60_000L, PermissionChangeOutboxBackoff.nextRetryAtMillis(100, now));
    }

    @Test
    @DisplayName("负重试次数不炸（按 0 处理）")
    void negativeRetryCountIsSafe() {
        long now = 1_000_000L;
        assertEquals(now + 1_000L, PermissionChangeOutboxBackoff.nextRetryAtMillis(-1, now));
    }
}
