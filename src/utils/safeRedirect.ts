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
 * 规则：只接受站内相对路径（以单个 `/` 开头）。`//host`（协议相对 URL）、
 * `http(s)://`、`javascript:` 等一律回退到默认页。
 *
 * @param uri - 待校验的跳转目标（任意来源）
 * @param fallback - 不合法时使用的站内默认页
 */
export function safeRedirect(uri: unknown, fallback = '/console/index'): string {
  const target = (uri ?? '').toString()
  if (!target) {
    return fallback
  }
  return target.startsWith('/') && !target.startsWith('//') ? target : fallback
}
