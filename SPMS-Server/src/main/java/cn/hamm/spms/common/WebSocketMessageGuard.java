package cn.hamm.spms.common;

import cn.hamm.spms.module.chat.enums.ChatEventType;

import java.util.Set;

/**
 * <h1>WebSocket 消息面输入守卫（Schema 校验 + 限流阈值）</h1>
 *
 * <p>聊天 WebSocket 是<b>已认证但未校验输入</b>的表面：airpower 框架在 {@code handleTextMessage}
 * 里只做 {@code Json.parse} 反序列化与异常兜底（异常仅记日志、不拒绝），随后直接把
 * {@code type}/{@code data} 交给 {@link AppWebSocketHandler#onWebSocketPayload}。这意味着：</p>
 * <ul>
 *   <li>客户端可发送任意 {@code type} 字符串（非数字会触发 {@link ChatEventType#getByStringKey}
 *       内部的 {@code Integer.parseInt} 异常，靠框架 catch 兜底，但这是「异常路径」而非「白名单」）；</li>
 *   <li>{@code data} 无长度上限，恶意客户端可投超大文本消息，经 Redis 频道<b>放大</b>广播给
 *       全房间成员（带宽/内存放大）。</li>
 * </ul>
 *
 * <p>本守卫在 {@code onWebSocketPayload} 入口做两层输入校验（纯静态、可测）：</p>
 * <ol>
 *   <li><b>事件类型白名单</b>：只放行「客户端→服务端」的三种事件（加入/离开/文本消息），
 *       其余（服务端→客户端事件、系统事件、未知值、非数字）一律丢弃；</li>
 *   <li><b>消息体长度上限</b>：{@code data} 超过 {@value #MAX_DATA_LENGTH} 字符即丢弃，
 *       堵住超大消息的频道放大。</li>
 * </ol>
 *
 * <p>发送限流的阈值常量（每用户每窗口最大消息数）也收在这里，实际计数由
 * {@code RateLimitHelper}（Redis 固定窗口）承担，Redis 不可用时降级放行——与公开端点限流同口径，
 * 只做防灌、不误伤正常聊天。</p>
 *
 * @author Hamm.cn
 */
public final class WebSocketMessageGuard {

    /** 单条消息 data 上限（字符）。聊天文本远小于此，8K 足够容纳；超过即视为异常 */
    public static final int MAX_DATA_LENGTH = 8192;

    /** 每用户每窗口最大消息数（防脚本刷屏；真人聊天 2 条/秒已很充裕） */
    public static final int MAX_MESSAGES_PER_MINUTE = 120;

    /** 限流窗口（秒） */
    public static final int RATE_LIMIT_WINDOW_SECONDS = 60;

    /** 限流 key 前缀（后接 userId） */
    public static final String RATE_LIMIT_KEY_PREFIX = "kubo:ws:msg:";

    /** 客户端→服务端合法事件 key（ROOM_MEMBER_JOIN / ROOM_MEMBER_LEAVE / ROOM_TEXT_MESSAGE） */
    private static final Set<Integer> CLIENT_EVENT_KEYS = Set.of(
            ChatEventType.ROOM_MEMBER_JOIN.getKey(),
            ChatEventType.ROOM_MEMBER_LEAVE.getKey(),
            ChatEventType.ROOM_TEXT_MESSAGE.getKey());

    private WebSocketMessageGuard() {
    }

    /**
     * 判断 type key 是否为「客户端→服务端」合法事件。
     *
     * <p>非数字、未知值、服务端→客户端事件（如 ROOM_JOIN_FAIL/ONLINE_COUNT_CHANGED）、
     * 系统事件（ONLINE/OFFLINE）都返回 false。</p>
     *
     * @param typeKey 事件类型字符串（来自 {@code WebSocketPayload.type}）
     * @return 合法客户端事件返回 true
     */
    public static boolean isClientEvent(String typeKey) {
        if (typeKey == null) {
            return false;
        }
        int key;
        try {
            key = Integer.parseInt(typeKey);
        } catch (NumberFormatException ignored) {
            return false;
        }
        return CLIENT_EVENT_KEYS.contains(key);
    }

    /**
     * 判断消息体是否超长。
     *
     * @param data 消息体（可为 null，null 视为不超长）
     * @return 超过 {@link #MAX_DATA_LENGTH} 返回 true
     */
    public static boolean isOversized(String data) {
        return data != null && data.length() > MAX_DATA_LENGTH;
    }
}
