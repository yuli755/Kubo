// @vitest-environment jsdom
import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope } from 'vue'

/**
 * 数据权限「盘点报表」系列 hook 的契约测试。
 *
 * 这些 hook 共享同一份形状（loading/failed/ready/items/count/preview + 自动刷新），
 * 但它们各自复刻了 timeOf/previewOf 两份小函数（未抽公共），因此各自都要锁一遍。
 * 这里锁的核心不变量（都是代码注释里明说的「失败取向」）：
 * 1. 自动刷新：挂载即拉取，items/count/preview 就位；
 * 2. 预览只取前 3 条，且「未知时间」用 0 时间戳退化；
 * 3. 刷新失败不清空上次结果（否则把「已知有记录」擦成「看起来没人改过」）；
 * 4. 死信的重放/丢弃触发后端后必须重新刷新列表。
 */

const getDenyLogs = vi.hoisted(() => vi.fn())
const getOperationLogs = vi.hoisted(() => vi.fn())
const getOutboxDead = vi.hoisted(() => vi.fn())
const replayOutbox = vi.hoisted(() => vi.fn())
const replayAllOutbox = vi.hoisted(() => vi.fn())
const discardOutbox = vi.hoisted(() => vi.fn())

vi.mock('@/api/system', () => ({
  dataResourceApi: {
    getDenyLogs,
    getOperationLogs,
    getOutboxDead,
    replayOutbox,
    replayAllOutbox,
    discardOutbox,
  },
}))

const { useDenyLogReport } = await import('@/composables/useDenyLogReport')
const { useOperationLogReport } = await import('@/composables/useOperationLogReport')
const { useOutboxDead } = await import('@/composables/useOutboxDead')

function denyItem(partial: Record<string, unknown>) {
  return { id: 1, createTime: 123, resourceKey: 'asset.material', reason: 'write-without-policy', mode: 'SHADOW', ...partial }
}

function opLogItem(partial: Record<string, unknown>) {
  return { id: 1, createTime: 123, resourceKey: 'asset.material', action: 'UPDATE', targetLabel: '物料A', ...partial }
}

function deadItem(partial: Record<string, unknown>) {
  return { id: 1, createTime: 123, eventType: 'policy', retryCount: 3, lastError: 'timeout', ...partial }
}

describe('reports · 拒绝事件盘点 useDenyLogReport', () => {
  let scope: ReturnType<typeof effectScope>

  beforeEach(() => {
    getDenyLogs.mockReset()
    scope = effectScope()
  })

  it('自动刷新：items/count/preview 就位，count=条数', async () => {
    getDenyLogs.mockResolvedValue([
      denyItem({ id: 1 }),
      denyItem({ id: 2, resourceKey: 'wms.inventory' }),
    ])
    const state = scope.run(() => useDenyLogReport())!
    await flushPromises()
    expect(state.ready).toBe(true)
    expect(state.count).toBe(2)
    expect(state.items).toHaveLength(2)
    expect(state.preview).toHaveLength(2)
  })

  it('预览只取前 3 条，并含资源/原因/档位', async () => {
    getDenyLogs.mockResolvedValue([
      denyItem({ id: 1 }),
      denyItem({ id: 2 }),
      denyItem({ id: 3 }),
      denyItem({ id: 4 }),
    ])
    const state = scope.run(() => useDenyLogReport())!
    await flushPromises()
    expect(state.preview).toHaveLength(3)
    expect(state.preview[0]).toContain('asset.material')
    expect(state.preview[0]).toContain('write-without-policy')
    expect(state.preview[0]).toContain('SHADOW')
  })

  it('0 时间戳退化为「未知时间」', async () => {
    getDenyLogs.mockResolvedValue([denyItem({ id: 1, createTime: 0 })])
    const state = scope.run(() => useDenyLogReport())!
    await flushPromises()
    expect(state.preview[0]).toContain('未知时间')
  })

  it('刷新失败不清空上次结果（失败取向）', async () => {
    getDenyLogs.mockResolvedValueOnce([denyItem({ id: 1 })])
    const state = scope.run(() => useDenyLogReport())!
    await flushPromises()
    expect(state.count).toBe(1)

    getDenyLogs.mockRejectedValueOnce(new Error('502'))
    await state.refresh()
    expect(state.failed).toBe(true)
    // 关键：失败不把「已知有记录」擦成「看起来没人改过」
    expect(state.items).toHaveLength(1)
    expect(state.count).toBe(1)
  })
})

describe('reports · 操作审计盘点 useOperationLogReport', () => {
  let scope: ReturnType<typeof effectScope>

  beforeEach(() => {
    getOperationLogs.mockReset()
    scope = effectScope()
  })

  it('自动刷新并生成「时间 · 资源 · 动作 · 目标」预览', async () => {
    getOperationLogs.mockResolvedValue([opLogItem({ id: 1 })])
    const state = scope.run(() => useOperationLogReport())!
    await flushPromises()
    expect(state.ready).toBe(true)
    expect(state.count).toBe(1)
    expect(state.preview[0]).toContain('asset.material')
    expect(state.preview[0]).toContain('UPDATE')
    expect(state.preview[0]).toContain('物料A')
  })

  it('后端返回空数组 = 没有记录（不是失败）', async () => {
    getOperationLogs.mockResolvedValue([])
    const state = scope.run(() => useOperationLogReport())!
    await flushPromises()
    expect(state.ready).toBe(true)
    expect(state.failed).toBe(false)
    expect(state.count).toBe(0)
  })
})

describe('reports · 死信盘点 useOutboxDead', () => {
  let scope: ReturnType<typeof effectScope>

  beforeEach(() => {
    getOutboxDead.mockReset()
    replayOutbox.mockReset()
    replayAllOutbox.mockReset()
    discardOutbox.mockReset()
    scope = effectScope()
  })

  it('自动刷新并生成「事件类型 · 重试次数 · 最后错误」预览', async () => {
    getOutboxDead.mockResolvedValue([deadItem({ id: 1 })])
    const state = scope.run(() => useOutboxDead())!
    await flushPromises()
    expect(state.count).toBe(1)
    expect(state.preview[0]).toContain('policy')
    expect(state.preview[0]).toContain('重试 3 次')
    expect(state.preview[0]).toContain('timeout')
  })

  it('lastError 缺省时退化为「无错误信息」', async () => {
    getOutboxDead.mockResolvedValue([deadItem({ id: 1, lastError: null })])
    const state = scope.run(() => useOutboxDead())!
    await flushPromises()
    expect(state.preview[0]).toContain('无错误信息')
  })

  it('replay / replayAll / discard 触发后端后重新刷新列表', async () => {
    getOutboxDead.mockResolvedValueOnce([])
    replayOutbox.mockResolvedValue(undefined)
    const state = scope.run(() => useOutboxDead())!
    await flushPromises()
    expect(getOutboxDead).toHaveBeenCalledTimes(1)

    await state.replay('evt-1')
    expect(replayOutbox).toHaveBeenCalledWith('evt-1')
    expect(getOutboxDead).toHaveBeenCalledTimes(2)

    await state.replayAll()
    expect(replayAllOutbox).toHaveBeenCalledTimes(1)

    await state.discard('evt-2')
    expect(discardOutbox).toHaveBeenCalledWith('evt-2')
  })

  it('重放失败把异常抛回调用方（特权动作失败必须可见，不吞）', async () => {
    getOutboxDead.mockResolvedValueOnce([])
    replayOutbox.mockRejectedValue(new Error('replay failed'))
    const state = scope.run(() => useOutboxDead())!
    await flushPromises()

    await expect(state.replay('evt-1')).rejects.toThrow('replay failed')
  })
})
