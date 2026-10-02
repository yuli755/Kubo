// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'

// jsdom 未实现 window.matchMedia，而主题 store 在模块加载时就读它
// （与 consoleMount.spec.ts 等模板级 spec 同款补桩，必须先于组件模块求值）
if (typeof window !== 'undefined' && !window.matchMedia) {
  window.matchMedia = ((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  })) as unknown as typeof window.matchMedia
}

/**
 * SecurityDashboard 图表哨兵（R44 ④）：把 R43 审计「同型隐患但不可达」的人肉判定
 * 升级为机器断言。
 *
 * 审计结论：`!modeChart`/`!parityChart` 守卫与 WarehouseHeatmap 的空白缺陷同型——
 * 触发需要 `resources.length` 走 0→非 0 让 v-if 重建容器；而唯一的数组替换者
 * refreshAll 只换数组不减长度，容器永不重建，同型缺陷在可达路径上不成立。
 * 本 spec 钉住这条可达契约：刷新替换数组 → **不重复 init、容器不变且仍挂载**；
 * 另钉卸载 dispose 配平。若将来接入「先清空再加载」的流程，测试 2 第一个变红。
 * 注意：这是哨兵不是修复——审计判定（NOT-WORTH-FIXING）维持，见报告 §19.5。
 */
interface FakeInstance {
  setOption: ReturnType<typeof vi.fn>
  resize: ReturnType<typeof vi.fn>
  dispose: ReturnType<typeof vi.fn>
  container: unknown
}

// vi.mock 工厂在 import 期求值，顶层变量届时未初始化 —— 借 hoisted 袋子中转
const H = vi.hoisted(() => ({
  instances: [] as unknown[],
  runtime: null as null | {
    ready: boolean
    loading: boolean
    resources: unknown[]
    refresh: () => void
  },
}))

vi.mock('@/utils/echarts', () => ({
  init: (el: unknown) => {
    const inst = { setOption: vi.fn(), resize: vi.fn(), dispose: vi.fn(), container: el }
    H.instances.push(inst)
    return inst
  },
}))

// 其余 8 个报告 composable 全部替成「已落定、零风险」形态，
// 本 spec 只关心图表生命周期，卡片渲染好坏与断言无关。
// runtime 必须 reactive —— 组件 watch 的是 [runtime.ready, runtime.resources.length]
vi.mock('@/composables/useDataPermissionRuntimeStatus', async () => {
  const { reactive } = await import('vue')
  H.runtime = reactive({
    ready: true,
    loading: false,
    refresh: vi.fn(),
    resources: [{ effectiveMode: 'ENFORCE' }, { effectiveMode: 'OFF' }],
  })
  return { useDataPermissionRuntimeStatus: () => H.runtime }
})

vi.mock('@/composables/useEnforceReadiness', async () => {
  const { reactive } = await import('vue')
  const s = reactive({ fetched: true, ready: true, failed: false, readyCount: 3, total: 3, preview: [], refresh: vi.fn() })
  return { useEnforceReadiness: () => s }
})
vi.mock('@/composables/useDenyLogReport', async () => {
  const { reactive } = await import('vue')
  const s = reactive({ ready: true, failed: false, count: 0, preview: [], refresh: vi.fn() })
  return { useDenyLogReport: () => s }
})
vi.mock('@/composables/useExportGapReport', async () => {
  const { reactive } = await import('vue')
  const s = reactive({ ready: true, failed: false, summary: { entries: 0, preview: [] }, refresh: vi.fn() })
  return { useExportGapReport: () => s }
})
vi.mock('@/composables/useInertPolicyReport', async () => {
  const { reactive } = await import('vue')
  const s = reactive({ ready: true, failed: false, count: 0, preview: [], refresh: vi.fn() })
  return { useInertPolicyReport: () => s }
})
vi.mock('@/composables/useDimensionBindingRiskReport', async () => {
  const { reactive } = await import('vue')
  const s = reactive({ ready: true, failed: false, count: 0, preview: [], refresh: vi.fn() })
  return { useDimensionBindingRiskReport: () => s }
})
vi.mock('@/composables/useRowPolicyRiskReport', async () => {
  const { reactive } = await import('vue')
  const s = reactive({ ready: true, failed: false, count: 0, preview: [], refresh: vi.fn() })
  return { useRowPolicyRiskReport: () => s }
})
vi.mock('@/composables/usePolicyResourceRiskReport', async () => {
  const { reactive } = await import('vue')
  const s = reactive({ ready: true, failed: false, count: 0, preview: [], refresh: vi.fn() })
  return { usePolicyResourceRiskReport: () => s }
})
vi.mock('@/composables/useOutboxDead', async () => {
  const { reactive } = await import('vue')
  const s = reactive({ ready: true, failed: false, count: 0, preview: [], refresh: vi.fn() })
  return { useOutboxDead: () => s }
})

async function mountDashboard() {
  const SecurityDashboard = (await import('@/view/console/index/SecurityDashboard.vue')).default
  const wrapper = mount(SecurityDashboard, { attachTo: document.body })
  await flushPromises()
  await nextTick()
  await flushPromises()
  return wrapper
}

function instances() {
  return H.instances as FakeInstance[]
}

beforeEach(() => {
  H.instances.length = 0
})

describe('index/SecurityDashboard · 图表实例可达性哨兵（R44）', () => {
  it('ready + 资源非空：两张图各 init 一次并 setOption', async () => {
    const wrapper = await mountDashboard()
    expect(instances()).toHaveLength(2)
    expect(instances()[0].setOption).toHaveBeenCalledTimes(1)
    expect(instances()[1].setOption).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('刷新替换数组（长度仍 >0 的唯一可达路径）：不重复 init，容器不游离（同型空白缺陷不可达的机器断言）', async () => {
    const wrapper = await mountDashboard()
    const before = instances().map(i => i.container)
    const firstSetOptions = instances().map(i => i.setOption.mock.calls.length)

    H.runtime!.resources = [{ effectiveMode: 'SHADOW' }, { effectiveMode: 'ENFORCE' }, { effectiveMode: 'OFF' }]
    await flushPromises()
    await nextTick()
    await flushPromises()

    // 守卫 !modeChart/!parityChart 挡住重复 init（缺陷同型路径的第一道闸）
    expect(instances()).toHaveLength(2)
    // 容器还是原容器且仍挂在文档上 —— 游离节点问题在可达路径上不成立
    expect(instances().map(i => i.container)).toEqual(before)
    expect(document.contains(before[0] as Node)).toBe(true)
    // 重绘走的是 setOption，不是换实例
    instances().forEach((i, k) => expect(i.setOption.mock.calls.length).toBeGreaterThan(firstSetOptions[k]))
    wrapper.unmount()
  })

  it('卸载：两实例 dispose 配平（审计 SAFE 路径的回归锁）', async () => {
    const wrapper = await mountDashboard()
    wrapper.unmount()
    expect(instances()[0].dispose).toHaveBeenCalledTimes(1)
    expect(instances()[1].dispose).toHaveBeenCalledTimes(1)
  })
})
