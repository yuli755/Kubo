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
    expect(safeRedirect('/console/index')).toBe('/console/index')
    expect(safeRedirect('/home?tab=1')).toBe('/home?tab=1')
    expect(safeRedirect('/console/wms/input/list')).toBe('/console/wms/input/list')
  })

  it('空值回退到默认页（含 undefined / null，调用方可能直传查询参数）', () => {
    expect(safeRedirect('')).toBe('/console/index')
    expect(safeRedirect(undefined)).toBe('/console/index')
    expect(safeRedirect(null)).toBe('/console/index')
  })

  it('协议相对地址 `//evil.test` 按外域处理', () => {
    expect(safeRedirect('//evil.test')).toBe('/console/index')
    expect(safeRedirect('//evil.test/console')).toBe('/console/index')
  })

  it('绝对 URL 与危险协议一律回退', () => {
    expect(safeRedirect('https://evil.test')).toBe('/console/index')
    expect(safeRedirect('http://evil.test/console')).toBe('/console/index')
    expect(safeRedirect('javascript:alert(1)')).toBe('/console/index')
    expect(safeRedirect('data:text/html,<script>alert(1)</script>')).toBe('/console/index')
  })

  it('相对路径（不以 / 开头）也算非法，防止拼出当前目录之外的地址', () => {
    expect(safeRedirect('console')).toBe('/console/index')
    expect(safeRedirect('../admin')).toBe('/console/index')
  })

  it('fallback 可由调用方指定', () => {
    expect(safeRedirect('', '/home')).toBe('/home')
    expect(safeRedirect('https://evil.test', '/home')).toBe('/home')
  })

  it('非字符串输入先 toString 再判定（数字/对象不会绕过判据）', () => {
    expect(safeRedirect(42)).toBe('/console/index')
    expect(safeRedirect({ toString: () => '//evil.test' })).toBe('/console/index')
  })

  // R-36（真机复刻 2026-10-01）：旧判据只看前缀，但 window.location 按 WHATWG 规范解析，
  // 会把下面这些「看着像站内」的写法规整成外域。判据必须与浏览器的解析器一致。
  it('r-36 反斜杠 / 制表符 / 换行变体不得绕过（旧实现会原样放行 → 会话送外域）', () => {
    const bs = String.fromCharCode(92)
    const tab = String.fromCharCode(9)
    const lf = String.fromCharCode(10)
    const cr = String.fromCharCode(13)
    const cases = [
      `/${bs}evil.test`, // /\evil.test → WHATWG 视为 //evil.test
      `/${bs}${bs}evil.test`,
      `/${tab}//evil.test`, // TAB 在解析前被剔除 → //evil.test
      `/${lf}//evil.test`,
      `/${cr}//evil.test`,
      `/${tab}${bs}evil.test`,
    ]
    for (const target of cases) {
      expect(safeRedirect(target), `raw=${JSON.stringify(target)}`).toBe('/console/index')
      // 同时钉住「若真放行就会跨 origin」这个前提，否则这组用例会在哪天悄悄失效
      if (target !== '/console/index') {
        const resolved = new URL(safeRedirect(target), 'http://localhost:3000').origin
        expect(resolved, `raw=${JSON.stringify(target)}`).toBe('http://localhost:3000')
      }
    }
  })

  it('r-36 终判用 URL 解析：编码反斜杠等仍留在站内的写法照常放行', () => {
    // %5C 是编码的反斜杠，解析后仍是站内路径，不该被误伤
    expect(safeRedirect('/%5Cevil.test')).toBe('/%5Cevil.test')
    // 正常的深层路径与查询参数不受影响
    expect(safeRedirect('/console/wms/input/list?page=2&status=%E5%AE%A1%E6%A0%B8%E4%B8%AD')).toBe(
      '/console/wms/input/list?page=2&status=%E5%AE%A1%E6%A0%B8%E4%B8%AD',
    )
  })
})
