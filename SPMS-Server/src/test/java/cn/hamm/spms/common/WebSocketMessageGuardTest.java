package cn.hamm.spms.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h1>WebSocketMessageGuard 契约测试（WebSocket 消息面输入校验）</h1>
 *
 * <p>锁住两条底线：客户端事件类型白名单（只放行加入/离开/文本消息），以及消息体长度上限
 * （堵住超大消息经 Redis 频道放大的滥用面）。</p>
 */
class WebSocketMessageGuardTest {

    @Test
    @DisplayName("客户端三事件（1001/1002/1100）均放行")
    void acceptsClientEvents() {
        assertTrue(WebSocketMessageGuard.isClientEvent("1001")); // ROOM_MEMBER_JOIN
        assertTrue(WebSocketMessageGuard.isClientEvent("1002")); // ROOM_MEMBER_LEAVE
        assertTrue(WebSocketMessageGuard.isClientEvent("1100")); // ROOM_TEXT_MESSAGE
    }

    @Test
    @DisplayName("服务端→客户端事件与系统事件均拒绝")
    void rejectsServerToClientAndSystemEvents() {
        assertFalse(WebSocketMessageGuard.isClientEvent("1003")); // ROOM_JOIN_FAIL
        assertFalse(WebSocketMessageGuard.isClientEvent("1004")); // ROOM_JOIN_SUCCESS
        assertFalse(WebSocketMessageGuard.isClientEvent("1050")); // ONLINE_COUNT_CHANGED
        assertFalse(WebSocketMessageGuard.isClientEvent("1"));    // ONLINE
        assertFalse(WebSocketMessageGuard.isClientEvent("2"));    // OFFLINE
    }

    @Test
    @DisplayName("未知值 / 非数字 / null 类型均拒绝")
    void rejectsUnknownOrMalformedType() {
        assertFalse(WebSocketMessageGuard.isClientEvent("9999")); // 未知数值
        assertFalse(WebSocketMessageGuard.isClientEvent("abc"));  // 非数字（getByStringKey 会 NFE）
        assertFalse(WebSocketMessageGuard.isClientEvent(""));
        assertFalse(WebSocketMessageGuard.isClientEvent(null));
    }

    @Test
    @DisplayName("消息体长度：边界内放行、超限拒绝、null 不超限")
    void enforcesDataLengthCap() {
        int max = WebSocketMessageGuard.MAX_DATA_LENGTH;
        assertFalse(WebSocketMessageGuard.isOversized(null));
        assertFalse(WebSocketMessageGuard.isOversized(""));
        assertFalse(WebSocketMessageGuard.isOversized("a".repeat(max)));     // 恰好上限
        assertTrue(WebSocketMessageGuard.isOversized("a".repeat(max + 1)));  // 超限
    }

    @Test
    @DisplayName("限流阈值常量语义自洽（每用户每窗口上限为正、窗口为正）")
    void rateLimitConstantsAreSane() {
        assertTrue(WebSocketMessageGuard.MAX_MESSAGES_PER_MINUTE > 0);
        assertTrue(WebSocketMessageGuard.RATE_LIMIT_WINDOW_SECONDS > 0);
        assertTrue(WebSocketMessageGuard.RATE_LIMIT_KEY_PREFIX.length() > 0);
    }
}
