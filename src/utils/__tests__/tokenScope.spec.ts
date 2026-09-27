// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from 'vitest'
import { clearTokenScope, enforceTokenScope, markTokenScope } from '@/utils/tokenScope'

/**
 * `tokenScope` 契约测试（G-3 · rememberMe 语义落地）。
 *
 * 复现三个真实场景（jsdom 的 localStorage/sessionStorage 与浏览器语义一致，
 * 「浏览器重启」= 清 sessionStorage 保留 localStorage）：
 * 1. 未勾选记住我 → 重启后 token 失效；
 * 2. 未勾选但同一会话内刷新 → token 保留；
 * 3. 勾选记住我 / 旧版无标记 → 重启后 token 保留（存量用户零感知）。
 */

const TOKEN_KEY = 'Authorization'
const SCOPE_KEY = 'kubo:token-scope'

function seedSession(remember: boolean): void {
  localStorage.clear()
  sessionStorage.clear()
  localStorage.setItem(TOKEN_KEY, 'tk-1')
  markTokenScope(remember)
}

/** 模拟浏览器重启：sessionStorage 清空，localStorage 存活 */
function restartBrowser(): void {
  sessionStorage.clear()
}

describe('tokenScope · rememberMe 语义', () => {
  beforeEach(() => {
    localStorage.clear()
    sessionStorage.clear()
  })

  it('未勾选记住我：浏览器重启后 token 与持久化快照一并清除', () => {
    seedSession(false)
    localStorage.setItem('kubo:user', '{"token":"tk-1"}')

    restartBrowser()
    expect(enforceTokenScope()).toBe(true)
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull()
    expect(localStorage.getItem(SCOPE_KEY)).toBeNull()
    expect(localStorage.getItem('kubo:user')).toBeNull()
  })

  it('未勾选记住我：同一浏览器会话内刷新不清 token', () => {
    seedSession(false)

    // 刷新只重建 JS 上下文，sessionStorage 存活
    expect(enforceTokenScope()).toBe(false)
    expect(localStorage.getItem(TOKEN_KEY)).toBe('tk-1')
  })

  it('勾选记住我：浏览器重启后 token 保留', () => {
    seedSession(true)

    restartBrowser()
    expect(enforceTokenScope()).toBe(false)
    expect(localStorage.getItem(TOKEN_KEY)).toBe('tk-1')
  })

  it('旧版存量数据（无作用域标记）：一律按记住处理', () => {
    localStorage.setItem(TOKEN_KEY, 'legacy-token')

    restartBrowser()
    expect(enforceTokenScope()).toBe(false)
    expect(localStorage.getItem(TOKEN_KEY)).toBe('legacy-token')
  })

  it('无 token 冷启动：顺手清掉残留标记', () => {
    localStorage.setItem(SCOPE_KEY, 'session')

    enforceTokenScope()
    expect(localStorage.getItem(SCOPE_KEY)).toBeNull()
  })

  it('clearTokenScope：登出后两个存储的标记都不残留', () => {
    seedSession(false)
    clearTokenScope()
    expect(localStorage.getItem(SCOPE_KEY)).toBeNull()
    expect(sessionStorage.getItem('kubo:token-live')).toBeNull()
  })
})
