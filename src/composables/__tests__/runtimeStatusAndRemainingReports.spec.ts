// @vitest-environment jsdom
import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope } from 'vue'

/**
 * 数据权限「业务概况 / 灰度状态总览 / 导出可见性 / 惰性策略 / 策略引用风险 / 行级不命中」
 * 六个 hook 的契约测试，补齐 composables 里剩余的零覆盖报表。
 *
 * 共同纪律（均为注释明说）：前端只搬运、不重算判定；失败取向 = 不清空上次结果、
 * 「不知道」绝不渲染成「没问题」。这里锁的核心不变量：
 * 1. 自动刷新、items/count/preview/summary 就位；
 * 2. 摘要/预览格式固定（只拼接）；
 * 3. 刷新失败不清空已有结论；
 * 4. useExportVisibility 的模块级缓存与 inflight 去重。
 */

const getOverview = vi.hoisted(() => vi.fn())
const getRuntimeStatus = vi.hoisted(() => vi.fn())
const getMyExportVisibility = vi.hoisted(() => vi.fn())
const getInertPolicies = vi.hoisted(() => vi.fn())
const getPolicyResourceRisks = vi.hoisted(() => vi.fn())
const getRowPolicyRisks = vi.hoisted(() => vi.fn())

vi.mock('@/api/system', () => ({
  dashboardApi: { getOverview },
  dataResourceApi: {
    getRuntimeStatus,
    getInertPolicies,
    getPolicyResourceRisks,
    getRowPolicyRisks,
  },
  dataFieldPolicyApi: { getMyExportVisibility },
}))

const { useBusinessOverview } = await import('@/composables/useBusinessOverview')
const { useDataPermissionRuntimeStatus } = await import('@/composables/useDataPermissionRuntimeStatus')
const { useExportVisibility } = await import('@/composables/useExportVisibility')
const { useInertPolicyReport } = await import('@/composables/useInertPolicyReport')
const { usePolicyResourceRiskReport } = await import('@/composables/usePolicyResourceRiskReport')
const { useRowPolicyRiskReport } = await import('@/composables/useRowPolicyRiskReport')

function inert(partial: Record<string, unknown>) {
  return { resourceKey: 'asset.material', fieldName: 'price', actionName: 'DETAIL', roleCode: 'viewer', ...partial }
}

function policyResourceRisk(partial: Record<string, unknown>) {
  return { policyCode: 'POL-1', kind: 'orphan', ...partial }
}

function rowPolicyRisk(partial: Record<string, unknown>) {
  return { resourceKey: 'asset.material', policyCode: 'POL-2', kind: 'bad-action', ...partial }
}

function runtimeStatus(partial: Record<string, unknown> = {}) {
  return {
    globalMode: 'shadow',
    enforceResources: ['asset.material', 'wms.inventory'],
    rowWriteEnabled: true,
    hierarchyDepartmentEnabled: false,
    denyUnregistered: true,
    resources: [],
    unmanagedServiceKeys: [],
    ...partial,
  }
}

describe('reports · 业务概况 useBusinessOverview', () => {
  let scope: ReturnType<typeof effectScope>

  beforeEach(() => {
    getOverview.mockReset()
    scope = effectScope()
  })

  it('自动刷新：items 就位、ready=true', async () => {
    getOverview.mockResolvedValue([{ resourceKey: 'asset.material', count: 42 }])
    const state = scope.run(() => useBusinessOverview())!
    await flushPromises()

    expect(state.ready).toBe(true)
    expect(state.items).toHaveLength(1)
  })

  it('刷新失败：failed=true 且不清空已有 items（不渲染成「全部为 0」）', async () => {
    getOverview.mockResolvedValueOnce([{ resourceKey: 'asset.material', count: 42 }])
    const state = scope.run(() => useBusinessOverview())!
    await flushPromises()
    expect(state.items).toHaveLength(1)

    getOverview.mockRejectedValueOnce(new Error('502'))
    await state.refresh()
    expect(state.failed).toBe(true)
    expect(state.items).toHaveLength(1)
  })
})

describe('reports · 灰度状态总览 useDataPermissionRuntimeStatus', () => {
  let scope: ReturnType<typeof effectScope>

  beforeEach(() => {
    getRuntimeStatus.mockReset()
    scope = effectScope()
  })

  it('摘要只拼接：全局模式 + 名单 + 三个开关的中文文案', async () => {
    getRuntimeStatus.mockResolvedValue(runtimeStatus())
    const state = scope.run(() => useDataPermissionRuntimeStatus())!
    await flushPromises()

    expect(state.ready).toBe(true)
    expect(state.summary).toContain('全局模式 shadow')
    expect(state.summary).toContain('enforce 名单 2 项：asset.material、wms.inventory')
    expect(state.summary).toContain('写侧行级保护 开')
    expect(state.summary).toContain('部门层级展开 关')
    expect(state.summary).toContain('未登记 拒绝')
  })

  it('空名单显示「（空）」，notes 只统计有提示的资源并取前 3', async () => {
    getRuntimeStatus.mockResolvedValue(runtimeStatus({
      enforceResources: [],
      resources: [
        { resourceKey: 'asset.material', notes: ['n1'] },
        { resourceKey: 'wms.inventory', notes: ['n2'] },
        { resourceKey: 'mes.order', notes: ['n3'] },
        { resourceKey: 'mes.plan', notes: ['n4'] },
        { resourceKey: 'mes.routing', notes: [] },
      ],
    }))
    const state = scope.run(() => useDataPermissionRuntimeStatus())!
    await flushPromises()

    expect(state.summary).toContain('enforce 名单 0 项：（空）')
    expect(state.pendingCount).toBe(4)
    expect(state.notePreview).toEqual([
      'asset.material：n1',
      'wms.inventory：n2',
      'mes.order：n3',
    ])
  })

  it('刷新失败：failed=true 不清空已有 summary', async () => {
    getRuntimeStatus.mockResolvedValueOnce(runtimeStatus())
    const state = scope.run(() => useDataPermissionRuntimeStatus())!
    await flushPromises()
    expect(state.summary).toContain('全局模式 shadow')

    getRuntimeStatus.mockRejectedValueOnce(new Error('502'))
    await state.refresh()
    expect(state.failed).toBe(true)
    expect(state.summary).toContain('全局模式 shadow')
  })
})

describe('reports · 导出可见性 useExportVisibility', () => {
  beforeEach(() => {
    getMyExportVisibility.mockReset()
  })

  it('未缓存时自动取数：fields 就位，blocked=未授权明文的受限字段', async () => {
    getMyExportVisibility.mockResolvedValue({ price: true, cost: false, vendor: true })
    const scope = effectScope()
    const state = scope.run(() => useExportVisibility('asset.material'))!
    await flushPromises()

    expect(state.ready).toBe(true)
    expect(state.blocked).toEqual(['cost'])
    scope.stop()
  })

  it('取数失败：ready=false、blocked 清空（沉默，不渲染「可导出」）', async () => {
    getMyExportVisibility.mockRejectedValue(new Error('502'))
    const scope = effectScope()
    const state = scope.run(() => useExportVisibility('wms.inventory'))!
    await flushPromises()

    expect(state.ready).toBe(false)
    expect(state.blocked).toEqual([])
    scope.stop()
  })

  it('同 resourceKey 命中缓存：不再发起第二次请求', async () => {
    getMyExportVisibility.mockResolvedValue({ price: true })
    const scopeA = effectScope()
    scopeA.run(() => useExportVisibility('mes.order'))!
    await flushPromises()
    scopeA.stop()

    getMyExportVisibility.mockClear()
    const scopeB = effectScope()
    const state = scopeB.run(() => useExportVisibility('mes.order'))!
    await flushPromises()

    expect(getMyExportVisibility).not.toHaveBeenCalled()
    expect(state.ready).toBe(true)
    scopeB.stop()
  })
})

describe('reports · 惰性策略 useInertPolicyReport', () => {
  let scope: ReturnType<typeof effectScope>

  beforeEach(() => {
    getInertPolicies.mockReset()
    scope = effectScope()
  })

  it('预览「资源 · 字段（动作/角色）」，只取前 3', async () => {
    getInertPolicies.mockResolvedValue([
      inert({ id: 1 }),
      inert({ id: 2, fieldName: 'cost', roleCode: 'editor' }),
    ])
    const state = scope.run(() => useInertPolicyReport())!
    await flushPromises()

    expect(state.count).toBe(2)
    expect(state.preview).toEqual([
      'asset.material · price（DETAIL/viewer）',
      'asset.material · cost（DETAIL/editor）',
    ])
  })

  it('刷新失败：failed=true 不清空已有 count', async () => {
    getInertPolicies.mockResolvedValueOnce([inert({ id: 1 })])
    const state = scope.run(() => useInertPolicyReport())!
    await flushPromises()
    expect(state.count).toBe(1)

    getInertPolicies.mockRejectedValueOnce(new Error('502'))
    await state.refresh()
    expect(state.failed).toBe(true)
    expect(state.count).toBe(1)
  })
})

describe('reports · 策略引用风险 usePolicyResourceRiskReport', () => {
  let scope: ReturnType<typeof effectScope>

  beforeEach(() => {
    getPolicyResourceRisks.mockReset()
    scope = effectScope()
  })

  it('预览「策略编码（类别）」', async () => {
    getPolicyResourceRisks.mockResolvedValue([
      policyResourceRisk({ id: 1 }),
      policyResourceRisk({ id: 2, policyCode: 'POL-2', kind: 'disabled' }),
    ])
    const state = scope.run(() => usePolicyResourceRiskReport())!
    await flushPromises()

    expect(state.count).toBe(2)
    expect(state.preview).toEqual(['POL-1（orphan）', 'POL-2（disabled）'])
  })

  it('刷新失败：failed=true 不清空已有 count', async () => {
    getPolicyResourceRisks.mockResolvedValueOnce([policyResourceRisk({ id: 1 })])
    const state = scope.run(() => usePolicyResourceRiskReport())!
    await flushPromises()
    expect(state.count).toBe(1)

    getPolicyResourceRisks.mockRejectedValueOnce(new Error('502'))
    await state.refresh()
    expect(state.failed).toBe(true)
    expect(state.count).toBe(1)
  })
})

describe('reports · 行级不命中 useRowPolicyRiskReport', () => {
  let scope: ReturnType<typeof effectScope>

  beforeEach(() => {
    getRowPolicyRisks.mockReset()
    scope = effectScope()
  })

  it('预览「资源 · 编码（类别）」', async () => {
    getRowPolicyRisks.mockResolvedValue([
      rowPolicyRisk({ id: 1 }),
      rowPolicyRisk({ id: 2, resourceKey: 'wms.inventory', kind: 'missing-subject' }),
    ])
    const state = scope.run(() => useRowPolicyRiskReport())!
    await flushPromises()

    expect(state.count).toBe(2)
    expect(state.preview).toEqual([
      'asset.material · POL-2（bad-action）',
      'wms.inventory · POL-2（missing-subject）',
    ])
  })

  it('刷新失败：failed=true 不清空已有 count', async () => {
    getRowPolicyRisks.mockResolvedValueOnce([rowPolicyRisk({ id: 1 })])
    const state = scope.run(() => useRowPolicyRiskReport())!
    await flushPromises()
    expect(state.count).toBe(1)

    getRowPolicyRisks.mockRejectedValueOnce(new Error('502'))
    await state.refresh()
    expect(state.failed).toBe(true)
    expect(state.count).toBe(1)
  })
})
