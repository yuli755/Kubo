// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { clearAllDrafts, DRAFT_KEY_PREFIX, draftKeyOf } from '@/utils/draftStorage'

/**
 * `draftStorage` 契约测试（第 35 轮审计）。
 *
 * 锁的是登出侧的清理半径：`clearAllDrafts` 必须**只**清 `kubo:draft:` 前缀的键。
 * 误伤 `Authorization` / `kubo:user` 会让「先清草稿再摘 token」的调用顺序变成
 * 双重登出竞态；漏清则上一个账号未提交的表单（含客户手机号）会被下一个账号
 * 的同号对话框恢复出来 —— 两头都要钉住。
 */

beforeEach(() => {
  localStorage.clear()
})

describe('draftStorage · 键与清理半径', () => {
  it('draftKeyOf 用统一前缀拼 service 名', () => {
    expect(draftKeyOf('wms:input')).toBe(`${DRAFT_KEY_PREFIX}wms:input`)
  })

  it('clearAllDrafts 清掉全部草稿，不碰其他键', () => {
    localStorage.setItem(draftKeyOf('wms:input'), '{"data":{}}')
    localStorage.setItem(draftKeyOf('channel:sale'), '{"data":{}}')
    localStorage.setItem('Authorization', 't-abc')
    localStorage.setItem('kubo:user', '{}')
    localStorage.setItem('kubo:theme', 'dark')

    clearAllDrafts()

    expect(localStorage.getItem(draftKeyOf('wms:input'))).toBeNull()
    expect(localStorage.getItem(draftKeyOf('channel:sale'))).toBeNull()
    expect(localStorage.getItem('Authorization')).toBe('t-abc')
    expect(localStorage.getItem('kubo:user')).toBe('{}')
    expect(localStorage.getItem('kubo:theme')).toBe('dark')
  })

  it('存储不可用（禁用/抛错）时静默返回，不炸登出流程', () => {
    const spy = vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => {
      throw new Error('SecurityError')
    })
    localStorage.setItem(draftKeyOf('a'), '{}')
    expect(() => clearAllDrafts()).not.toThrow()
    spy.mockRestore()
  })
})
