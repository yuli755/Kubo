/**
 * # WebSocket 消息载荷
 *
 * 与后端 `cn.hamm.airpower.websocket.WebSocketPayload` 对齐：
 * - `type` 事件类型（默认 `system`）
 * - `data` 序列化后的字符串（通常为 JSON）
 *
 * 后端约定：客户端发送的文本帧要么是心跳 `PING`，要么是 `WebSocketPayload` 的 JSON。
 */
export interface WebSocketPayload<T = string> {
  /** 事件类型，后端 `setType()`，默认 `system` */
  type: string
  /** 载荷数据，后端 `setData()` 接收字符串（业务侧通常为 JSON.toString 结果） */
  data: T
}

/**
 * # WebSocket 连接状态
 */
export enum WebSocketStatus {
  /** 未连接 */
  DISCONNECTED = 'disconnected',
  /** 连接中 */
  CONNECTING = 'connecting',
  /** 已连接 */
  CONNECTED = 'connected',
  /** 重连中 */
  RECONNECTING = 'reconnecting',
  /** 已主动关闭 */
  CLOSED = 'closed',
}

/**
 * # 消息处理器
 *
 * 收到指定 `type` 的事件时被调用，`payload` 为原始载荷对象。
 */
export type WebSocketMessageHandler = (payload: WebSocketPayload) => void
