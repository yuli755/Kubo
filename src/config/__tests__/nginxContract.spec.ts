import { readFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * # nginx 反向代理契约门禁（S4 补充 · 运行时可达性）
 *
 * `cspContract.spec.ts` 锁的是「安全头与 CSP」，这里锁「服务可达性」——nginx 作为生产入口，
 * 前端依赖的后端通道（REST `/api/`）与静态资源通道都必须被正确反代，
 * 否则是「本地绿、线上功能静默失效」的运行时问题，静态 lint/typecheck 抓不到。
 *
 * 锁的两条：
 * 1. `/api/` 必须反代到 backend；
 * 2. `/static/`（用户上传文件，可能含 SVG/HTML）必须显式 nosniff——nginx 的 add_header 不跨
 *    location 继承，缺它会回到 MIME 嗅探，上传的 SVG 可被当 HTML 执行（存储型 XSS 面）。
 *
 * 注：WebSocket 通道（`/websocket`）与聊天模块已于 2026-09-17 整体移除，
 * 相关契约随之删除；如将来新增实时通道（SSE/MQ 推送），需在此重新加契约。
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

describe('nginx contract · 反向代理可达性（静态上传）', () => {
  it('/static/ 显式 nosniff（add_header 不跨 location 继承，上传文件防 MIME 嗅探）', () => {
    const body = locationBody(read(), '/static/')
    expect(
      body,
      '/static/ 缺 X-Content-Type-Options: nosniff：上传的 SVG/HTML 可被嗅探为 HTML 执行，存储型 XSS 面',
    ).toMatch(/X-Content-Type-Options\s+"nosniff"/)
  })
})
