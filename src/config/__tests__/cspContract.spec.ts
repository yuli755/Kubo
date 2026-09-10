import { createHash } from 'node:crypto'
import { readFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * # 前端 CSP 契约门禁（S4 · 第三批：nginx 托管层）
 *
 * 后端 `SecurityHeadersFilter` 只覆盖 `/api/`（JSON/WebSocket），前端 HTML/JS/CSS 由 nginx
 * 托管、不经过后端 filter，因此 CSP 与 X-Frame-Options 必须加在 nginx 层。这里锁住几个
 * 「改了配置就白屏 / 改了内联脚本就失效」的不变量，让机器在提交前拦住，而不是等上线后猜。
 *
 * 锁的三条：
 * 1. nginx 前端层必须有 CSP 头（Report-Only 起步）与 X-Frame-Options: DENY（防点击劫持）；
 * 2. CSP 的 `script-src` 用 sha256 精确放行 index.html 内联防闪烁脚本 —— 若有人改内联脚本
 *    却没同步 hash，强制化那一刻就是白屏，且报错只在生产才显形；
 * 3. nginx 不得开目录列表（autoindex on 会把静态目录结构暴露给访问者）。
 */

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', '..')
const NGINX = join(ROOT, 'nginx.conf')
const INDEX = join(ROOT, 'index.html')

const read = (path: string): string => readFileSync(path, 'utf-8')

/** 提取 index.html 里的内联 <script> 内容（首个，即防闪烁脚本） */
function inlineScriptBody(html: string): string {
  const m = /<script>([\s\S]*?)<\/script>/.exec(html)
  expect(m, 'index.html 里找不到内联 <script>：CSP hash 门禁会空转').not.toBeNull()
  return m![1]
}

describe('csp contract · 前端 CSP（nginx 托管层）', () => {
  it('nginx 前端层必须有 CSP 头（Report-Only 起步，不阻断加载）', () => {
    const conf = read(NGINX)
    expect(
      conf,
      'nginx 缺 Content-Security-Policy 头：前端 HTML 由 nginx 托管、不经后端 filter，CSP 只能加在这里',
    ).toMatch(/Content-Security-Policy/)
  })

  it('csp script-src 的 sha256 必须与 index.html 内联脚本一致（改内联脚本须同步 hash）', () => {
    const conf = read(NGINX)
    const html = read(INDEX)
    const hash = createHash('sha256').update(inlineScriptBody(html)).digest('base64')
    expect(
      conf,
      `CSP 里的 sha256 与 index.html 内联脚本不一致：改了内联 <script> 却未同步 hash，强制化后 script-src 会阻断防闪烁脚本（表现为白屏/主题错乱）`,
    ).toContain(`'sha256-${hash}'`)
  })

  it('script-src 不得回退到 unsafe-inline（那样 hash 形同虚设）', () => {
    const conf = read(NGINX)
    const csp = /Content-Security-Policy-Report-Only[^;]*(?:;|")/.exec(conf)?.[0] ?? conf
    expect(
      csp,
      'script-src 出现 unsafe-inline：等于放弃对内联脚本的约束，XSS 防护大打折扣',
    ).not.toMatch(/script-src[^;]*unsafe-inline/)
  })

  it('nginx 前端层有 X-Frame-Options: DENY（防点击劫持，后端只出 JSON 加不了这个头）', () => {
    const conf = read(NGINX)
    expect(
      conf,
      'nginx 缺 X-Frame-Options: DENY：管理后台可被第三方 iframe 嵌入做点击劫持',
    ).toMatch(/X-Frame-Options\s+"DENY"/)
  })

  it('nginx 不得开启目录列表（autoindex on 暴露静态目录结构）', () => {
    const conf = read(NGINX)
    // 只检查指令行，跳过注释（注释里会解释"autoindex on 会暴露结构"）
    const directive = conf
      .split('\n')
      .map(l => l.trim())
      .find(l => /^autoindex\s+/i.test(l))
    expect(
      directive,
      'nginx 开了 autoindex on：目录列表会把静态资源结构暴露给访问者（攻击面测绘来源）',
    ).not.toMatch(/^autoindex\s+on/i)
  })
})
