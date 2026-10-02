// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { safeGetItem, safeRemoveItem, safeSetItem } from '@/utils/storage'

/**
 * `storage` 契约测试（第 35 轮审计）。
 *
 * 锁的是「写便利数据绝不该炸掉交互」：隐私模式下 Safari/Firefox 的 localStorage
 * 配额直接为 0，`setItem` 当场抛 QuotaExceededError；企业策略禁用 DOM Storage 时
 * 连 `getItem` 都会抛。此前 theme / KAppShell / authStorage 三处裸调用 ——
 * 用户只是想切个暗色主题，却弹出一条报错。这里锁：三种操作在存储抛错时
 * 一律静默降级，且**只吞存储异常**，正常路径行为与原生一致。
 */

beforeEach(() => {
  localStorage.clear()
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('storage · 安全读写', () => {
  it('正常路径：set/get/remove 与原生行为一致', () => {
    expect(safeSetItem('k', 'v')).toBe(true)
    expect(localStorage.getItem('k')).toBe('v')
    expect(safeGetItem('k')).toBe('v')
    safeRemoveItem('k')
    expect(safeGetItem('k')).toBeNull()
  })

  it('配额抛错（隐私模式）：safeSetItem 返回 false 而不抛出', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('QuotaExceededError')
    })
    expect(() => safeSetItem('k', 'v')).not.toThrow()
    expect(safeSetItem('k', 'v')).toBe(false)
  })

  it('存储被禁用（读也抛）：safeGetItem 退化为 null，safeRemoveItem 静默', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('SecurityError')
    })
    vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => {
      throw new Error('SecurityError')
    })
    expect(safeGetItem('k')).toBeNull()
    expect(() => safeRemoveItem('k')).not.toThrow()
  })
})
