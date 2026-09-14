import { readFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * # nginx 反向代理契约门禁（S4 补充 · 运行时可达性）
 *
 * `cspContract.spec.ts` 锁的是「安全头与 CSP」，这里锁「服务可达性」——nginx 作为生产入口，
 * 前端依赖的两条后端通道（REST `/api/` 与 WebSocket `/websocket`）都必须被正确反代，
 * 否则是「本地绿、线上功能静默失效」的运行时问题，静态 lint/typecheck 抓不到。
 *
 * 锁的三条：
 * 1. `/websocket` 必须有独立 location 反代到 backend（带 Upgrade/Connection 头 + HTTP/1.1）；
 *    缺失时前端 `ws(s)://<host>/websocket` 会落到 location / 的 try_files 被当静态文件，握手失败；
 * 2. `/static/`（用户上传文件，可能含 SVG/HTML）必须显式 nosniff——nginx 的 add_header 不跨
 *    location 继承，缺它会回到 MIME 嗅探，上传的 SVG 可被当 HTML 执行（存储型 XSS 面）；
 * 3. WebSocket 反代必须 `proxy_http_version 1.1`（HTTP/1.0 默认下 Upgrade 不生效）。
 */

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', '..')
const NGINX = join(ROOT, 'nginx.conf')

const read = (): string => readFileSync(NGINX, 'utf-8')

/** 提取某 location 块的内容（到最近的闭合大括号） */
function locationBody(conf: string, name: string): string {
  const start = conf.indexOf(`location ${name}`)
  expect(start, `nginx.conf 缺 location ${name} 块`).toBeGreaterThanOrEqual(0)
  // 从 location 行的 { 开始括号配平到闭合
  let depth = 0
  let i = conf.indexOf('{', start)
  for (; i < conf.length; i++) {
    if (conf[i] === '{')
      depth++
    else if (conf[i] === '}' && --depth === 0)
      break
  }
  return conf.slice(start, i + 1)
}

describe('nginx contract · 反向代理可达性（WebSocket + 静态上传）', () => {
  it('/websocket 有独立反代 location（否则生产推送失效）', () => {
    const body = locationBody(read(), '/websocket')
    expect(
      body,
      '/websocket 未反代到 backend：前端生产同源连接会落到 try_files 被当静态文件，WebSocket 握手失败',
    ).toMatch(/proxy_pass\s+http:\/\/backend/)
  })

  it('/websocket 反代声明 Upgrade/Connection 升级头', () => {
    const body = locationBody(read(), '/websocket')
    expect(body).toMatch(/proxy_set_header\s+Upgrade\s+\$http_upgrade/)
    expect(body).toMatch(/proxy_set_header\s+Connection\s+"upgrade"/)
  })

  it('/websocket 反代用 HTTP/1.1（默认 1.0 下 Upgrade 不生效）', () => {
    const body = locationBody(read(), '/websocket')
    expect(body).toMatch(/proxy_http_version\s+1\.1/)
  })

  it('/static/ 显式 nosniff（add_header 不跨 location 继承，上传文件防 MIME 嗅探）', () => {
    const body = locationBody(read(), '/static/')
    expect(
      body,
      '/static/ 缺 X-Content-Type-Options: nosniff：上传的 SVG/HTML 可被嗅探为 HTML 执行，存储型 XSS 面',
    ).toMatch(/X-Content-Type-Options\s+"nosniff"/)
  })
})
