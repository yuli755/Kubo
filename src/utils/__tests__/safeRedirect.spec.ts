import { describe, expect, it } from 'vitest'
import { safeRedirect } from '@/utils/safeRedirect'

/**
 * 开放重定向防护（P0 安全件）
 *
 * `/login` 是 guest 路由，`?redirect=` / `?redirectUri=` 由访问者控制。
 * 这里逐条钉住「什么算站内相对路径」，因为任何一条判据松掉，
 * 登录成功后就会把用户（和会话）送到攻击者域。
 */
describe('safeRedirect 开放重定向防护', () => {
  it('站内相对路径原样放行', () => {
    expect(safeRedirect('/console')).toBe('/console')
    expect(safeRedirect('/home?tab=1')).toBe('/home?tab=1')
    expect(safeRedirect('/console/wms/input/list')).toBe('/console/wms/input/list')
  })

  it('空值回退到默认页（含 undefined / null，调用方可能直传查询参数）', () => {
    expect(safeRedirect('')).toBe('/console')
    expect(safeRedirect(undefined)).toBe('/console')
    expect(safeRedirect(null)).toBe('/console')
  })

  it('协议相对地址 `//evil.test` 按外域处理', () => {
    expect(safeRedirect('//evil.test')).toBe('/console')
    expect(safeRedirect('//evil.test/console')).toBe('/console')
  })

  it('绝对 URL 与危险协议一律回退', () => {
    expect(safeRedirect('https://evil.test')).toBe('/console')
    expect(safeRedirect('http://evil.test/console')).toBe('/console')
    expect(safeRedirect('javascript:alert(1)')).toBe('/console')
    expect(safeRedirect('data:text/html,<script>alert(1)</script>')).toBe('/console')
  })

  it('相对路径（不以 / 开头）也算非法，防止拼出当前目录之外的地址', () => {
    expect(safeRedirect('console')).toBe('/console')
    expect(safeRedirect('../admin')).toBe('/console')
  })

  it('fallback 可由调用方指定', () => {
    expect(safeRedirect('', '/home')).toBe('/home')
    expect(safeRedirect('https://evil.test', '/home')).toBe('/home')
  })

  it('非字符串输入先 toString 再判定（数字/对象不会绕过判据）', () => {
    expect(safeRedirect(42)).toBe('/console')
    expect(safeRedirect({ toString: () => '//evil.test' })).toBe('/console')
  })
})
