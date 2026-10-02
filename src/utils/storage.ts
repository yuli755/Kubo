/**
 * # localStorage 安全写入
 *
 * `localStorage.setItem` 会在两类常见场景抛异常：
 * 1. **配额满**（QuotaExceededError）—— 隐私模式下 Safari/Firefox 直接配额为 0；
 * 2. **存储被禁用**（无痕模式、企业策略禁用 DOM Storage）—— 部分旧浏览器直接抛 SecurityError。
 *
 * 这些调用点写的都是**便利数据**（主题偏好、侧栏折叠态、token 镜像），写失败的正确
 * 处理是「本次会话内继续生效，不落盘而已」，绝不该把整个交互（切主题、点折叠、
 * 登录）炸成报错。此前 theme.ts / KAppShell.vue / authStorage 三处都是裸
 * `setItem` —— 全项目仅此三种写法，收成一个函数，新增调用点照抄即可。
 */

/** 写一条 localStorage；失败（配额/禁用）静默降级，返回是否成功便于调试 */
export function safeSetItem(key: string, value: string): boolean {
  try {
    localStorage.setItem(key, value)
    return true
  }
  catch {
    return false
  }
}

/** 读一条 localStorage；存储被禁用时抛异常的读路径同样要兜底 */
export function safeGetItem(key: string): string | null {
  try {
    return localStorage.getItem(key)
  }
  catch {
    return null
  }
}

/** 删一条 localStorage；不存在/禁用都不算错 */
export function safeRemoveItem(key: string): void {
  try {
    localStorage.removeItem(key)
  }
  catch {
    // 与 get 同理：禁用场景下静默
  }
}
