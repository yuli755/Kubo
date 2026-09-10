// @vitest-environment jsdom
import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope } from 'vue'

/**
 * 数据权限「放开就绪 / 导出缺口 / 维度绑定风险」三个盘点 hook 的契约测试。
 *
 * 与 reports.spec.ts 互补 —— 那份锁的是拒绝/审计/死信三条，这份锁的是
 * 「决定能不能放开 enforce」与「配置哪里漏了」这两类更靠决策的报表。
 * 核心不变量（都是注释里明说的失败取向 / 无判定语义）：
 * 1. 自动刷新、items/count/preview/summary 就位；
 * 2. 预览只取前若干条、格式固定（只拼接、不重算判定）；
 * 3. 刷新失败不清空上次结果、且不把「未取到」显示成「全部就绪/无缺口/无风险」；
 * 4. gapsForField 纯函数只筛选、不判定。
 */

const getEnforceReadiness = vi.hoisted(() => vi.fn())
const getExportGaps = vi.hoisted(() => vi.fn())
const getDimensionBindingRisks = vi.hoisted(() => vi.fn())

vi.mock('@/api/system', () => ({
  dataResourceApi: {
    getEnforceReadiness,
    getExportGaps,
    getDimensionBindingRisks,
  },
}))

const { useEnforceReadiness } = await import('@/composables/useEnforceReadiness')
const { useExportGapReport, gapsForField } = await import('@/composables/useExportGapReport')
const { useDimensionBindingRiskReport } = await import('@/composables/useDimensionBindingRiskReport')

function readiness(partial: Record<string, unknown>) {
  return { resourceKey: 'asset.material', ready: false, blockers: ['缺少 UPDATE 策略'], ...partial }
}

function gap(partial: Record<string, unknown>) {
  return { resourceKey: 'asset.material', resourceId: 1, fieldName: 'price', roleCode: 'default', reason: '缺 EXPORT 授权', ...partial }
}

function bindingRisk(partial: Record<string, unknown>) {
  return { resourceKey: 'asset.material', subjectType: 'TENANT', kind: 'missing-column', ...partial }
}

describe('reports · 放开就绪 useEnforceReadiness', () => {
  let scope: ReturnType<typeof effectScope>

  beforeEach(() => {
    getEnforceReadiness.mockReset()
    scope = effectScope()
  })

  it('自动刷新：total/readyCount/fetched 就位，preview 只列未就绪且含首个阻塞原因', async () => {
    getEnforceReadiness.mockResolvedValue([
      readiness({ resourceKey: 'asset.material', ready: true, blockers: [] }),
      readiness({ resourceKey: 'wms.inventory', ready: false, blockers: ['缺少 UPDATE 策略', '维度绑不上列'] }),
    ])
    const state = scope.run(() => useEnforceReadiness())!
    await flushPromises()

    expect(state.fetched).toBe(true)
    expect(state.total).toBe(2)
    expect(state.readyCount).toBe(1)
    expect(state.preview).toEqual(['wms.inventory：缺少 UPDATE 策略'])
  })

  it('刷新失败：failed=true 且 fetched 保持 false（绝不显示成「全部就绪」）', async () => {
    getEnforceReadiness.mockRejectedValueOnce(new Error('502'))
    const state = scope.run(() => useEnforceReadiness())!
    await flushPromises()

    expect(state.failed).toBe(true)
    expect(state.fetched).toBe(false)
    expect(state.readyCount).toBe(0)
  })

  it('单对象响应兜底为数组（后端单资源/全部两种返回都接得住）', async () => {
    getEnforceReadiness.mockResolvedValue(readiness({ resourceKey: 'asset.material', ready: true, blockers: [] }))
    const state = scope.run(() => useEnforceReadiness())!
    await flushPromises()

    expect(state.total).toBe(1)
    expect(state.readyCount).toBe(1)
  })
})

describe('reports · 导出缺口 useExportGapReport', () => {
  let scope: ReturnType<typeof effectScope>

  beforeEach(() => {
    getExportGaps.mockReset()
    scope = effectScope()
  })

  it('按资源分组计数、按缺口数降序、preview 取前 3「资源（N 处）」', async () => {
    getExportGaps.mockResolvedValue([
      gap({ resourceKey: 'asset.material', resourceId: 1, fieldName: 'x' }),
      gap({ resourceKey: 'wms.inventory', resourceId: 2, fieldName: 'x' }),
      gap({ resourceKey: 'wms.inventory', resourceId: 2, fieldName: 'y' }),
      gap({ resourceKey: 'wms.inventory', resourceId: 2, fieldName: 'z' }),
    ])
    const state = scope.run(() => useExportGapReport())!
    await flushPromises()

    expect(state.summary.resources).toBe(2)
    expect(state.summary.entries).toBe(4)
    expect(state.summary.preview).toEqual(['wms.inventory（3 处）', 'asset.material（1 处）'])
  })

  it('刷新失败：failed=true，summary 不被清空成「无缺口」', async () => {
    getExportGaps.mockResolvedValueOnce([gap({ resourceKey: 'asset.material', resourceId: 1, fieldName: 'x' })])
    const state = scope.run(() => useExportGapReport())!
    await flushPromises()
    expect(state.summary.entries).toBe(1)

    getExportGaps.mockRejectedValueOnce(new Error('502'))
    await state.refresh()
    expect(state.failed).toBe(true)
    expect(state.summary.entries).toBe(1)
  })

  it('gapsForField 纯筛选：按 (resourceId, fieldName) 精确匹配，参数缺失返回空', () => {
    const items = [
      gap({ resourceKey: 'asset.material', resourceId: 1, fieldName: 'price' }),
      gap({ resourceKey: 'asset.material', resourceId: 1, fieldName: 'cost' }),
      gap({ resourceKey: 'asset.material', resourceId: 2, fieldName: 'price' }),
    ]

    expect(gapsForField(items, 1, 'price')).toHaveLength(1)
    expect(gapsForField(items, 1, 'price')[0].fieldName).toBe('price')

    expect(gapsForField(items, undefined, 'price')).toHaveLength(0)
    expect(gapsForField(items, 1, undefined)).toHaveLength(0)
    expect(gapsForField(items, 1, '')).toHaveLength(0)
  })
})

describe('reports · 维度绑定风险 useDimensionBindingRiskReport', () => {
  let scope: ReturnType<typeof effectScope>

  beforeEach(() => {
    getDimensionBindingRisks.mockReset()
    scope = effectScope()
  })

  it('预览「资源 · 维度（类别）」，subjectType 缺省退化为「(整表)」', async () => {
    getDimensionBindingRisks.mockResolvedValue([
      bindingRisk({ resourceKey: 'asset.material', subjectType: 'TENANT', kind: 'missing-column' }),
      bindingRisk({ resourceKey: 'wms.inventory', subjectType: null, kind: 'no-join-path' }),
    ])
    const state = scope.run(() => useDimensionBindingRiskReport())!
    await flushPromises()

    expect(state.count).toBe(2)
    expect(state.preview).toEqual([
      'asset.material · TENANT（missing-column）',
      'wms.inventory · (整表)（no-join-path）',
    ])
  })

  it('刷新失败：failed=true 不清空已有 count', async () => {
    getDimensionBindingRisks.mockResolvedValueOnce([bindingRisk({ resourceKey: 'asset.material' })])
    const state = scope.run(() => useDimensionBindingRiskReport())!
    await flushPromises()
    expect(state.count).toBe(1)

    getDimensionBindingRisks.mockRejectedValueOnce(new Error('502'))
    await state.refresh()
    expect(state.failed).toBe(true)
    expect(state.count).toBe(1)
  })
})
