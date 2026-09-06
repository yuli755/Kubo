// @vitest-environment jsdom
import { createPinia, setActivePinia } from 'pinia'
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref, withDirectives } from 'vue'

// v-permission 依赖 airpower 的 PermissionUtil 做持久化；单元测试里隔离掉，
// 只验证「指令 → 显隐」这层本项目自己的逻辑。
vi.mock('@airpower/web', () => ({
  PermissionUtil: { saveList: vi.fn(), checkPermission: vi.fn(() => true) },
}))

const { vPermission } = await import('../permission')
const { usePermissionStore } = await import('@/stores/permission')

/**
 * # v-permission 指令契约
 *
 * 这是后续「33 个列表页批量补 v-permission」的安全网：
 * 一旦这里语义变化（例如无权限改成 disabled 而不是隐藏、或数组语义从 OR 变 AND），
 * 所有视图行为都会跟着变，必须先过这条测试。
 *
 * 用 withDirectives + h 而不是 template，避免依赖 Vue 运行时编译器。
 */
function mountHarness(required: ReturnType<typeof ref<string[] | undefined>>) {
  const Comp = defineComponent({
    setup() {
      return () => withDirectives(h('button', null, '审核'), [[vPermission, required.value]])
    },
  })
  return mount(Comp)
}

describe('v-permission', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  const isHidden = (el: Element) => (el as HTMLElement).style.display === 'none'

  it('无权限 → 隐藏元素', () => {
    usePermissionStore().setPermissions([])
    const required = ref(['Order_audit'])
    const w = mountHarness(required)
    expect(isHidden(w.element)).toBe(true)
  })

  it('命中任一权限（数组 = OR 语义）→ 显示元素', () => {
    usePermissionStore().setPermissions(['Order_audit', 'WmsInventory_export'])
    const w = mountHarness(ref(['Order_audit', 'Order_reject']))
    expect(isHidden(w.element)).toBe(false)
  })

  it('单值字符串也可用', () => {
    usePermissionStore().setPermissions(['Order_audit'])
    expect(isHidden(mountHarness(ref('Order_audit')).element)).toBe(false)
    usePermissionStore().setPermissions([])
    expect(isHidden(mountHarness(ref('Order_audit')).element)).toBe(true)
  })

  it('未传值 → 不拦截（避免误伤未纳入权限体系的按钮）', () => {
    const w = mountHarness(ref(undefined))
    expect(isHidden(w.element)).toBe(false)
  })

  it('权限被撤销后重渲染 → 恢复隐藏（updated 分支生效）', async () => {
    usePermissionStore().setPermissions(['Order_audit'])
    const required = ref<string[]>(['Order_audit'])
    const w = mountHarness(required)
    expect(isHidden(w.element)).toBe(false)

    usePermissionStore().setPermissions([])
    required.value = ['Order_audit']
    await nextTick()
    expect(isHidden(w.element)).toBe(true)
  })
})
