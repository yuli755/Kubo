import { airpowerToken } from '@/utils/airpowerCompat'

/**
 * # Token 作用域（rememberMe 语义落地）
 *
 * 背景：airpower 的 token 存取硬编码 `localStorage`（跨浏览器重启存活），登录页的
 * 「记住我」勾选框过去只影响 UI，不改变任何持久化行为——不勾选也会 7 天免登录，
 * 语义虚假。
 *
 * 本模块在不 fork airpower 的前提下用「作用域标记」实现真实语义：
 * - token 仍只存 `localStorage`（airpower 组件同步读取，这一硬约束不动）；
 * - `kubo:token-scope`（localStorage）记录登录时的选择：`session` | `persistent`；
 * - `kubo:token-live`（sessionStorage）标记「当前浏览器会话仍然活跃」——
 *   sessionStorage 在同标签页刷新后存活、浏览器重启后清空，正是我们要的分界线。
 *
 * 冷启动时 {@link enforceTokenScope} 判定：scope 为 session 且没有同会话的 live 标记
 * → 浏览器已重启 → 清除 token（等价于会话结束）。persistent / 旧版数据（无标记）
 * 一律按「记住」处理，存量用户零感知。
 */

const SCOPE_KEY = 'kubo:token-scope'
const LIVE_KEY = 'kubo:token-live'

type TokenScope = 'session' | 'persistent'

/** 登录成功后调用：按「记住我」勾选状态登记本次会话的作用域。 */
export function markTokenScope(remember: boolean): void {
  const scope: TokenScope = remember ? 'persistent' : 'session'
  localStorage.setItem(SCOPE_KEY, scope)
  if (scope === 'session') {
    sessionStorage.setItem(LIVE_KEY, '1')
  }
  else {
    sessionStorage.removeItem(LIVE_KEY)
  }
}

/** 登出时调用：清掉作用域标记，避免残留影响下次登录判定。 */
export function clearTokenScope(): void {
  localStorage.removeItem(SCOPE_KEY)
  sessionStorage.removeItem(LIVE_KEY)
}

/**
 * ## 作用域自检（导出仅为便于测试，生产入口在 main.ts 冷启动处调用一次）。
 *
 * @returns 是否清除了过期的 session 级 token（供测试断言）
 */
export function enforceTokenScope(): boolean {
  if (!airpowerToken.get()) {
    clearTokenScope()
    return false
  }
  if (localStorage.getItem(SCOPE_KEY) !== 'session') {
    // persistent 或旧版无标记数据：按「记住」处理，存量会话不强制下线
    return false
  }
  if (sessionStorage.getItem(LIVE_KEY)) {
    // 同一浏览器会话内的刷新/重开页面：会话仍活跃，token 保留
    return false
  }
  // session 级登录 + 浏览器已重启：会话结束，token 与持久化快照一并清除
  airpowerToken.remove()
  localStorage.removeItem(SCOPE_KEY)
  localStorage.removeItem('kubo:user')
  airpowerToken.remove()
  clearTokenScope()
  return true
}
