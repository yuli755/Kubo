// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils'
import { ElRadioGroup } from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import WarehouseHeatmap from '@/view/console/wms/inventory/component/WarehouseHeatmap.vue'

/**
 * 热力矩阵图表实例的生命周期回归（R43 缺陷 1，view 层第一枚组件测试）。
 *
 * 原缺陷：容器在 v-else 分支上，切到 2.5D 时 DOM 节点被销毁而 echarts 实例还在；
 * 切回矩阵时 `if (!chartInstance)` 跳过重建 → setOption 画在游离节点上，
 * **第二次进矩阵永远空白**，旧实例连 click 闭包泄漏到组件卸载。
 * 修复：离开 matrix 模式即 dispose + 置空，回来时自然走全新 init。
 *
 * `@/utils/echarts` 整体 mock 掉（jsdom 里 canvas 不可用，真 echarts 也测不出
 * 游离节点问题）——替身记录 init 次数、每次返回新实例并记下容器元素，断言只围绕
 * 「init/dispose 的配平与容器新鲜度」，这正是缺陷的骨架。
 * vue-router 未安装时 useRouter() 返回 undefined 也不炸（只在点击里用），
 * 但为确定性仍给替身。
 */
interface FakeInstance {
  setOption: ReturnType<typeof vi.fn>
  on: ReturnType<typeof vi.fn>
  resize: ReturnType<typeof vi.fn>
  dispose: ReturnType<typeof vi.fn>
  container: unknown
}

const instances: FakeInstance[] = []

vi.mock('@/utils/echarts', () => ({
  init: (el: unknown) => {
    const inst: FakeInstance = {
      setOption: vi.fn(),
      on: vi.fn(),
      resize: vi.fn(),
      dispose: vi.fn(),
      container: el,
    }
    instances.push(inst)
    return inst
  },
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
}))

function mountHeatmap() {
  return mount(WarehouseHeatmap, {
    props: { storageId: 1, storageName: '测试仓' },
    // 必须挂到真实文档：本测试的核心断言是「容器是否游离」，
    // 默认挂载在游离容器上，document.contains 恒为 false（首轮实测踩过）
    attachTo: document.body,
  })
}

/** 切换视角模式并冲刷「watcher → nextTick(渲染/销毁)」两级微任务 */
async function setMode(wrapper: ReturnType<typeof mountHeatmap>, mode: 'isometric' | 'matrix') {
  wrapper.findAllComponents(ElRadioGroup)[0].vm.$emit('update:modelValue', mode)
  await nextTick()
  await flushPromises()
  await nextTick()
}

beforeEach(() => {
  instances.length = 0
})

describe('wms/WarehouseHeatmap · echarts 实例随模式切换配平（R43）', () => {
  it('挂载默认 2.5D：不建图', async () => {
    const wrapper = mountHeatmap()
    await flushPromises()
    expect(instances).toHaveLength(0)
    wrapper.unmount()
  })

  it('2.5D → 矩阵：全新 init + setOption + click 绑定', async () => {
    const wrapper = mountHeatmap()
    await setMode(wrapper, 'matrix')
    expect(instances).toHaveLength(1)
    expect(instances[0].setOption).toHaveBeenCalled()
    expect(instances[0].on).toHaveBeenCalledWith('click', expect.any(Function))
    wrapper.unmount()
  })

  it('矩阵 → 2.5D → 矩阵：旧实例被销毁，新实例画在新容器上（空白缺陷回归锁）', async () => {
    const wrapper = mountHeatmap()
    await setMode(wrapper, 'matrix')
    const firstContainer = instances[0].container as HTMLElement
    expect(document.contains(firstContainer)).toBe(true)

    await setMode(wrapper, 'isometric')
    // 缺陷修复点：离开矩阵即销毁，而不是留一个指向游离 DOM 的僵尸实例
    expect(instances[0].dispose).toHaveBeenCalledTimes(1)

    await setMode(wrapper, 'matrix')
    // 修复前这里不会有第二实例（guard 跳过重建），画面永远空白
    expect(instances).toHaveLength(2)
    const secondContainer = instances[1].container as HTMLElement
    expect(secondContainer).not.toBe(firstContainer)
    expect(document.contains(secondContainer)).toBe(true)
    expect(instances[1].setOption).toHaveBeenCalled()
    wrapper.unmount()
  })

  it('矩阵模式下卸载：dispose 兜底仍然生效（原路径不回归）', async () => {
    const wrapper = mountHeatmap()
    await setMode(wrapper, 'matrix')
    wrapper.unmount()
    expect(instances[0].dispose).toHaveBeenCalledTimes(1)
  })
})
