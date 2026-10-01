/**
 * # 开放重定向防护
 *
 * `/login` 是 guest 路由，`redirectUri`/`redirect` 查询参数完全由访问者控制。
 * 直接把用户提供的字符串拼进 `window.location.href` 等于把登录后的会话
 * 交给攻击者：`?redirectUri=https://evil.test` 会把用户送到外部域。
 *
 * 开放平台（`/authorize` + `?appKey=`）已整体移除，`/login` 不再有
 * 「OAuth 内部应用可跳登记回调」的例外——这里只剩一条判定路径。
 *
 * ## 为什么不能只做字符串前后缀判断（R-36，真机复刻 2026-10-01）
 * 旧实现只判 `startsWith('/') && !startsWith('//')`，但 `window.location` 按
 * **WHATWG URL 规范**解析目标，而该规范会把多种写法规整成「另一个 origin」：
 * - `/\evil.test`：反斜杠等同斜杠 → `http://evil.test/`
 * - `/<TAB>//evil.test`、`/<LF>//evil.test`：制表符与换行在解析前被剔除 → `http://evil.test/`
 * 于是旧实现把这三种形态原样放行，登录后会话直接送到外域。
 * `//evil.test` 拦住了，`/\evil.test` 漏了 —— 差异完全在「我们的判据」与
 * 「浏览器的判据」不是同一件事。
 *
 * ## 现在的做法：让浏览器自己的解析器当裁判
 * 1. 先拒绝含反斜杠 / 控制字符（含 TAB、LF、CR）的目标——它们正是上面那些
 *    「看着像站内、解析成外域」的载面，且不存在任何合法路由会用到；
 * 2. 再用 `new URL(target, FAKE_BASE)` 按同一规范解析，**终判看 origin**：
 *    只有 `origin` 与基准域完全一致才放行。
 * 这样判据与浏览器行为由同一个解析器定义，以后规范再出新的规整规则也不会
 * 悄悄绕过我们——因为绕不过 origin 比对。
 *
 * 注：这里用固定哨兵域做基准而不依赖 `window.location`，是为了保持纯函数
 * （node 环境可直接单测）；跨 origin 的判定与当前站点无关，站内相对路径永远
 * 解析回哨兵域。
 *
 * @param uri - 待校验的跳转目标（任意来源）
 * @param fallback - 不合法时使用的站内默认页
 */

/** 哨兵基准域：只为得到一个可比较的 origin，不代表真实站点。 */
const FAKE_BASE = 'http://safe-redirect.invalid'

/** 反斜杠与 C0/C1 控制字符：URL 解析器会剔除或当斜杠处理，没有合法站内路径会用到。 */
// eslint-disable-next-line no-control-regex -- 这里要拦的**正是**控制字符：WHATWG URL 解析会剔除 TAB/LF/CR 把「看着像站内」的写法规整成外域，合法路由不可能用到，命中即回退
const FORBIDDEN_CHARS = /[\\\u0000-\u001F\u007F-\u009F]/

export function safeRedirect(uri: unknown, fallback = '/console/index'): string {
  const target = (uri ?? '').toString()
  if (!target) {
    return fallback
  }
  // 协议相对（`//host`）与绝对 URL / 危险协议在这一步就被挡下
  if (!target.startsWith('/') || target.startsWith('//')) {
    return fallback
  }
  if (FORBIDDEN_CHARS.test(target)) {
    return fallback
  }
  try {
    const resolved = new URL(target, FAKE_BASE)
    if (resolved.origin !== FAKE_BASE) {
      return fallback
    }
  }
  catch {
    return fallback
  }
  return target
}
