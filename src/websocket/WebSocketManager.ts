import type { WebSocketMessageHandler, WebSocketPayload } from './types'
import { ref } from 'vue'
import { useUserStore } from '@/stores/user'
import { reportError } from '@/utils/errorHandler'
import { WebSocketStatus } from './types'

/**
 * # WebSocket 管理器
 *
 * 与后端 `cn.hamm.airpower.websocket.WebSocketHandler`（airpower 原生 WebSocket，**非 STOMP**）对齐：
 *
 * ## 连接协议
 * - 端点：`ws(s)://<host>/websocket?accessToken=<token>`
 *   后端在 `afterConnectionEstablished` 中读取 `session.getUri().getQuery()`，
 *   交由 `AccessTokenUtil.verify(query, secret)` 校验，取出 `payloadId`（即 userId）。
 *   因此 token 必须以**查询参数**形式携带（浏览器原生 WebSocket 不支持自定义请求头）。
 *
 * ## 消息格式
 * - 心跳：客户端发送文本帧 `PING`，后端 `handleTextMessage` 命中 `getPing()`（默认 `PING`）后回 `PONG`。
 * - 业务：文本帧为 `WebSocketPayload` 的 JSON `{ type, data }`，后端 `Json.parse` 后交给
 *   `onWebSocketPayload(payload, session)` 处理。
 *
 * ## 重连策略
 * 参考 `WEBSOCKET_PROTOCOL.md`：指数退避，baseDelay 1000ms，maxDelay 30000ms，
 * 增长系数 1.5，附加 0–30% 抖动，最多 10 次。
 *
 * ## 频道订阅
 * 后端通过 `subscribe(channel, session)` / `unsubscribe(channel, session)` 管理频道，
 * 客户端发送 `{ type: 'subscribe'|'unsubscribe', data: channel }` 由后端业务处理器实现
 * （如聊天房间 `ROOM_MEMBER_JOIN` 触发 `subscribe`）。本管理器只负责收发，频道的语义由业务层约定。
 *
 * @author Hamm.cn
 */
class WebSocketManager {
  /** WebSocket 实例 */
  private socket: WebSocket | null = null

  /** 响应式连接状态 */
  readonly status = ref<WebSocketStatus>(WebSocketStatus.DISCONNECTED)

  /** 是否由用户主动关闭（主动关闭时不触发自动重连） */
  private manuallyClosed = false

  /** 当前重连次数 */
  private reconnectAttempts = 0

  /** 重连定时器 */
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null

  /** 心跳定时器 */
  private heartbeatTimer: ReturnType<typeof setInterval> | null = null

  /** 消息处理器映射：type -> handlers */
  private readonly handlers = new Map<string, Set<WebSocketMessageHandler>>()

  /** 通配处理器（监听所有 type） */
  private readonly wildcardHandlers = new Set<WebSocketMessageHandler>()

  /** 连接状态变更回调 */
  private readonly statusListeners = new Set<(status: WebSocketStatus) => void>()

  /**
   * ### 构造 WebSocket URL
   *
   * 开发环境通过 vite 代理 `/websocket`（ws:true）转发到后端；
   * 生产环境直接使用 `location` 同源地址。
   */
  private buildUrl(): string {
    const token = useUserStore().token
    if (!token) {
      throw new Error('无法建立 WebSocket 连接：缺少访问令牌')
    }

    const { location } = window
    const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
    // 开发环境 vite 代理 /websocket，生产环境同源直连后端
    const host = import.meta.env.DEV ? location.host : location.host
    const params = new URLSearchParams({ accessToken: token })
    return `${protocol}//${host}/websocket?${params.toString()}`
  }

  /**
   * ### 建立连接
   *
   * @returns 是否成功发起新连接（已在连接中返回 false）
   */
  connect(): boolean {
    if (this.socket && this.status.value !== WebSocketStatus.DISCONNECTED) {
      return false
    }
    if (!useUserStore().token) {
      return false
    }

    this.manuallyClosed = false
    this.setStatus(
      this.reconnectAttempts > 0
        ? WebSocketStatus.RECONNECTING
        : WebSocketStatus.CONNECTING,
    )

    try {
      const url = this.buildUrl()
      this.socket = new WebSocket(url)
    }
    catch (error) {
      reportError(error, 'websocket-connect')
      this.scheduleReconnect()
      return false
    }

    this.socket.onopen = this.onOpen.bind(this)
    this.socket.onmessage = this.onMessage.bind(this)
    this.socket.onclose = this.onClose.bind(this)
    this.socket.onerror = this.onError.bind(this)

    return true
  }

  /**
   * ### 连接成功回调
   */
  private onOpen(): void {
    this.reconnectAttempts = 0
    this.setStatus(WebSocketStatus.CONNECTED)
    this.startHeartbeat()
  }

  /**
   * ### 接收消息
   *
   * 1. 收到 `PONG` —— 心跳响应，忽略
   * 2. 否则解析为 `WebSocketPayload`，分发到对应处理器
   */
  private onMessage(event: MessageEvent): void {
    const raw = typeof event.data === 'string' ? event.data : ''
    if (!raw) {
      return
    }

    // 心跳响应
    if (raw === 'PONG') {
      return
    }

    let payload: WebSocketPayload
    try {
      payload = JSON.parse(raw) as WebSocketPayload
    }
    catch (error) {
      console.warn('[WebSocket] 无法解析消息，期望 JSON 格式', raw, error)
      return
    }

    this.dispatch(payload)
  }

  /**
   * ### 分发消息到处理器
   */
  private dispatch(payload: WebSocketPayload): void {
    const typed = this.handlers.get(payload.type)
    if (typed) {
      typed.forEach(handler => this.safeCall(handler, payload))
    }
    this.wildcardHandlers.forEach(handler => this.safeCall(handler, payload))
  }

  /**
   * ### 安全调用处理器（捕获异常避免影响其他处理器）
   */
  private safeCall(handler: WebSocketMessageHandler, payload: WebSocketPayload): void {
    try {
      handler(payload)
    }
    catch (error) {
      reportError(error, 'websocket-handler')
    }
  }

  /**
   * ### 连接关闭回调
   */
  private onClose(_event: CloseEvent): void {
    this.stopHeartbeat()
    this.socket = null

    if (this.manuallyClosed) {
      this.setStatus(WebSocketStatus.CLOSED)
      return
    }

    // 非主动关闭 —— 触发自动重连
    this.scheduleReconnect()
  }

  /**
   * ### 连接错误回调
   */
  private onError(event: Event): void {
    reportError(event, 'websocket-error')
    // onclose 会随后被触发，由其统一处理重连
  }

  /**
   * ### 调度重连（指数退避 + 抖动）
   */
  private scheduleReconnect(): void {
    const maxAttempts = 10
    if (this.reconnectAttempts >= maxAttempts) {
      console.warn('[WebSocket] 已达最大重连次数，停止重连')
      this.setStatus(WebSocketStatus.DISCONNECTED)
      return
    }

    const baseDelay = 1000
    const maxDelay = 30000
    const growth = 1.5
    const delay = Math.min(baseDelay * growth ** this.reconnectAttempts, maxDelay)
    // 0–30% 抖动
    const jitter = delay * (Math.random() * 0.3)
    const finalDelay = Math.round(delay + jitter)

    this.reconnectAttempts++
    this.setStatus(WebSocketStatus.RECONNECTING)
    console.warn(`[WebSocket] ${finalDelay}ms 后进行第 ${this.reconnectAttempts} 次重连`)

    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null
      this.connect()
    }, finalDelay)
  }

  /**
   * ### 启动心跳
   *
   * 每 30s 发送一次 `PING`，后端收到后回 `PONG`。
   */
  private startHeartbeat(): void {
    this.stopHeartbeat()
    this.heartbeatTimer = setInterval(() => {
      this.sendRaw('PING')
    }, 30000)
  }

  /**
   * ### 停止心跳
   */
  private stopHeartbeat(): void {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = null
    }
  }

  /**
   * ### 发送原始文本帧
   */
  private sendRaw(text: string): boolean {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) {
      return false
    }
    try {
      this.socket.send(text)
      return true
    }
    catch (error) {
      reportError(error, 'websocket-send')
      return false
    }
  }

  /**
   * ### 发送业务消息
   *
   * @param type 事件类型
   * @param data 载荷（对象会被 JSON.stringify，字符串原样发送）
   * @returns 是否发送成功
   */
  send(type: string, data: unknown): boolean {
    const payload: WebSocketPayload = {
      type,
      data: typeof data === 'string' ? data : JSON.stringify(data),
    }
    return this.sendRaw(JSON.stringify(payload))
  }

  /**
   * ### 订阅消息
   *
   * @param type 事件类型，`*` 表示监听全部
   * @returns 取消订阅函数
   */
  on(type: string, handler: WebSocketMessageHandler): () => void {
    if (type === '*') {
      this.wildcardHandlers.add(handler)
      return () => this.wildcardHandlers.delete(handler)
    }

    let set = this.handlers.get(type)
    if (!set) {
      set = new Set()
      this.handlers.set(type, set)
    }
    set.add(handler)
    return () => set!.delete(handler)
  }

  /**
   * ### 监听连接状态变更
   */
  onStatusChange(listener: (status: WebSocketStatus) => void): () => void {
    this.statusListeners.add(listener)
    return () => this.statusListeners.delete(listener)
  }

  private setStatus(status: WebSocketStatus): void {
    this.status.value = status
    this.statusListeners.forEach((listener) => {
      try {
        listener(status)
      }
      catch (error) {
        reportError(error, 'websocket-listener')
      }
    })
  }

  /**
   * ### 主动关闭连接
   *
   * 主动关闭后不会自动重连。
   */
  disconnect(): void {
    this.manuallyClosed = true
    this.stopHeartbeat()
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
    this.reconnectAttempts = 0
    if (this.socket) {
      this.socket.onclose = null
      this.socket.onerror = null
      this.socket.onmessage = null
      this.socket.onopen = null
      try {
        this.socket.close()
      }
      catch {
        // 忽略关闭异常
      }
      this.socket = null
    }
    this.setStatus(WebSocketStatus.CLOSED)
  }

  /**
   * ### 是否已连接
   */
  get isConnected(): boolean {
    return this.status.value === WebSocketStatus.CONNECTED
  }
}

/**
 * # WebSocket 管理器单例
 *
 * 全应用共享一个 WebSocket 连接。组件通过 `useWebSocket()` composable 访问。
 */
export const webSocketManager = new WebSocketManager()
