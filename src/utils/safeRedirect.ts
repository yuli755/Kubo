/**
 * # 开放重定向防护
 *
 * `/login`、`/authorize` 都是 guest 路由，`redirectUri`/`redirect` 查询参数
 * 完全由访问者控制。直接把用户提供的字符串拼进 `window.location.href`
 * （且带上 `?code=` 授权码）等于把 OAuth code / 会话后跳转交给攻击者：
 * `?redirectUri=https://evil.test` 会把 code 明文送到外部域。
 *
 * 规则：只接受站内相对路径（以单个 `/` 开头）。`//host`（协议相对 URL）、
 * `http(s)://`、`javascript:` 等一律回退到默认页。
 *
 * @param uri - 待校验的跳转目标（任意来源）
 * @param fallback - 不合法时使用的站内默认页
 */
export function safeRedirect(uri: unknown, fallback = '/console'): string {
  const target = (uri ?? '').toString()
  if (!target) {
    return fallback
  }
  return target.startsWith('/') && !target.startsWith('//') ? target : fallback
}
