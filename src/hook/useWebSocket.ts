import type { Ref } from 'vue'
import type { WebSocketMessageHandler, WebSocketPayload, WebSocketStatus } from '@/websocket/types'
import { onScopeDispose } from 'vue'
import { webSocketManager } from '@/websocket/WebSocketManager'

/**
 * # WebSocket Composable
 *
 * 基于 {@link webSocketManager} 单例提供响应式连接状态与事件订阅。
 *
 * 组件卸载时自动取消订阅（通过 `onScopeDispose`），但**不会**断开连接
 * （连接由应用级生命周期管理，见 `main.ts`）。
 *
 * @example
 * ```ts
 * const { status, isConnected, send, onMessage } = useWebSocket()
 *
 * // 监听指定事件
 * const off = onMessage('ROOM_TEXT_MESSAGE', (payload) => {
 *   console.log('收到消息', payload.data)
 * })
 * ```
 *
 * @author Hamm.cn
 */
export function useWebSocket() {
  /** 响应式连接状态 */
  const status: Ref<WebSocketStatus> = webSocketManager.status

  /** 是否已连接 */
  const isConnected = () => webSocketManager.isConnected

  /**
   * ### 发送业务消息
   */
  function send(type: string, data: unknown): boolean {
    return webSocketManager.send(type, data)
  }

  /**
   * ### 订阅消息
   *
   * @param type 事件类型，`*` 表示监听全部
   * @param handler 处理函数
   * @returns 取消订阅函数（作用域销毁时也会自动调用）
   */
  function onMessage(type: string, handler: WebSocketMessageHandler): () => void {
    const off = webSocketManager.on(type, handler)
    onScopeDispose(off)
    return off
  }

  /**
   * ### 主动发起连接（无 token 时静默失败）
   */
  function connect(): boolean {
    return webSocketManager.connect()
  }

  return {
    status,
    isConnected,
    send,
    onMessage,
    connect,
  }
}

export type { WebSocketPayload }
