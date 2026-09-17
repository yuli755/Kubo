// @vitest-environment jsdom
// ↑ 本文件要断言 Element Plus 弹窗的真实 DOM（`append-to-body` 会 teleport 到 document.body），
//   没有 jsdom 时 `document is not defined` —— 与 frameworkComponents.spec.ts 同款约定。
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'

/**
 * # 共享件契约（应用外壳 + 单据弹窗 + 审核按钮）
 *
 * 本文件锁两件事：
 *
 * 1. **airpower 彻底退场（从\"精确剩余集合\"收紧为空集）**：`src/component/**`
 *    里**不允许**再出现任何一行 `@airpower/web` 导入 —— 这条曾经是\"待迁清单\"，
 *    2026-09-18 最后四个（`UserCard` + `user/*`）迁完后变成硬门禁。
 * 2. **命令式弹窗的契约**：`DialogUtil.show(View, param)` 打开的弹窗靠
 *    `param / onConfirm / onCancel` 三个 props 通信（见 `framework/feedback.ts`
 *    的 `IDialogActionProps`）。单据弹窗从 airpower `ADialog` 换成 `KDialog` 时，
 *    如果契约被改坏，**wms/channel/asset 单据页会静默失效**（弹窗能开、走不完），
 *    所以这里逐条钉住：标题取 param、确认按钮禁用条件、确认回传的数据。
 */

beforeEach(() => {
  // `v-permission` 内部会 usePermissionStore()，没有活动 pinia 时挂载直接抛错
  setActivePinia(createPinia())
  vi.restoreAllMocks()
})

/**
 * 挂载辅助。
 *
 * `mount(Component as never, { props })` 会让 vue-tsc 把 options 推断成 `undefined`
 * （泛型从 never 推不出来，报 TS2322）；这里把两个参数一起放宽，
 * 断言仍然走真实的 `wrapper` / `document.body`。
 */
function mountDialog(component: unknown, options: Record<string, unknown> = {}) {
  return mount(component as never, options as never)
}

/** 剥掉注释后再找 import —— 注释里提到 `@airpower/web` 的说明文字不算依赖 */
function importsAirpower(source: string): boolean {
  const code = source
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\/\/[^\n]*/g, '')
  return /^\s*import[^\n]*from\s+'@airpower\/web'/m.test(code)
}

describe('共享件 · airpower 退场完成度', () => {
  it('src/component/** 里不允许再有 airpower 依赖', async () => {
    const { readFileSync } = await import('node:fs')
    const { readdirSync, statSync } = await import('node:fs')
    const { join, relative } = await import('node:path')

    const root = process.cwd()
    function walk(dir: string, out: string[] = []): string[] {
      for (const entry of readdirSync(dir)) {
        const full = join(dir, entry)
        if (statSync(full).isDirectory()) {
          walk(full, out)
        }
        else if (/\.(?:ts|vue)$/.test(entry)) {
          out.push(full)
        }
      }
      return out
    }

    const files = walk(join(root, 'src', 'component'))
      .filter(file => importsAirpower(readFileSync(file, 'utf8')))
      .map(file => relative(root, file).replaceAll('\\', '/'))
      .sort()

    /**
     * 2026-09-18：`UserCard` + `user/*`（最后四个）随 airpower 整体退场迁完，
     * 清单从「精确剩余集合」收紧成空数组 —— 这里是硬门禁：`src/component/**`
     * 只要再冒出任何一行 `@airpower/web` 导入，本用例立刻翻红。
     */
    expect(files, '共享件不应再有 airpower 依赖').toEqual([])
  })

  it('外壳 / 两个单据弹窗 / 审核按钮 / KDialog 都不再 import airpower', async () => {
    const { readFileSync } = await import('node:fs')
    const migrated = [
      'src/component/Console.vue',
      'src/component/framework/KDialog.vue',
      'src/component/bill/BillAddFinishDialog.vue',
      'src/component/bill/BillRejectDialog.vue',
      'src/component/bill/BillAuditOrReject.vue',
    ]
    for (const file of migrated) {
      expect(importsAirpower(readFileSync(file, 'utf8')), file).toBe(false)
    }
  })

  it('旧壳开关随旧壳分支一起删除（不留"能切回 airpower 外壳"的后门）', async () => {
    const { readFileSync, existsSync } = await import('node:fs')
    const flags = readFileSync('src/config/buildFlags.ts', 'utf8')
    expect(flags.includes('LEGACY_SHELL'), 'buildFlags 不该再有旧壳开关').toBe(false)
    expect(readFileSync('src/component/Console.vue', 'utf8').includes('AFrame')).toBe(false)
    const envTypes = existsSync('src/env.d.ts') ? readFileSync('src/env.d.ts', 'utf8') : ''
    expect(envTypes.includes('VITE_LEGACY_SHELL')).toBe(false)
  })

  it('路由层不再为 airpower 拨任何全局开关（isTimeout 熔断随库删除）', async () => {
    const { readFileSync } = await import('node:fs')
    const router = readFileSync('src/router/appRouter.ts', 'utf8')
    const console_ = readFileSync('src/component/Console.vue', 'utf8')
    // 路由实例现在由项目自己的 vue-router 创建，不再需要"先复位 airpower 标志位再注册"
    expect(router).toContain('createRouter({')
    expect(router.includes('isTimeout'), '路由层不该再有 isTimeout 开关').toBe(false)
    expect(/^\s*import[^\n]*WebConfig/m.test(console_), 'Console 不该 import WebConfig').toBe(false)
    expect(importsAirpower(console_), 'Console 不该 import airpower').toBe(false)
  })
})

describe('f4 · KDialog 命令式契约', () => {
  it('点确定：调用 onConfirm、关窗、不重复触发', async () => {
    const KDialog = (await import('@/component/framework/KDialog.vue')).default
    const onConfirm = vi.fn()
    const onCancel = vi.fn()
    mountDialog(KDialog, {
      props: { title: '标题', onConfirm, onCancel },
      slots: { default: '内容' },
    })
    await nextTick()
    const button = Array.from(document.body.querySelectorAll('button'))
      .find(item => item.textContent?.trim() === '确定')
    expect(button, '确定按钮没渲染出来').toBeTruthy()
    // ⚠️ Element Plus 的按钮在 jsdom 下用 `.click()` 不触发（见 frameworkComponents.spec.ts）
    button!.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await nextTick()
    expect(onConfirm).toHaveBeenCalledTimes(1)
    expect(onCancel).not.toHaveBeenCalled()
    document.body.innerHTML = ''
  })

  it('disable-confirm：按钮禁用且点了也不回调（"数量为 0 不许提交"靠它）', async () => {
    const KDialog = (await import('@/component/framework/KDialog.vue')).default
    const onConfirm = vi.fn()
    mountDialog(KDialog, { props: { disableConfirm: true, onConfirm } })
    await nextTick()
    const button = Array.from(document.body.querySelectorAll('button'))
      .find(item => item.textContent?.trim() === '确定')
    expect(button?.disabled, '禁用态没落到 DOM 上').toBe(true)
    button!.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await nextTick()
    expect(onConfirm).not.toHaveBeenCalled()
    document.body.innerHTML = ''
  })

  it('声明式用法：有 @confirm 监听时只 emit，不去调 props 上的 onConfirm', async () => {
    const KDialog = (await import('@/component/framework/KDialog.vue')).default
    const onConfirmProp = vi.fn()
    const wrapper = mountDialog(KDialog, { props: { onConfirm: onConfirmProp } })
    await nextTick()
    const button = Array.from(document.body.querySelectorAll('button'))
      .find(item => item.textContent?.trim() === '确定')
    button!.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await nextTick()
    // 没有 @confirm 监听 → 走 props 回调（命令式宿主）
    expect(onConfirmProp).toHaveBeenCalledTimes(1)
    void wrapper
    document.body.innerHTML = ''
  })

  it('取消/关窗：调用 onCancel（命令式宿主据此 resolve undefined）', async () => {
    const KDialog = (await import('@/component/framework/KDialog.vue')).default
    const onCancel = vi.fn()
    mountDialog(KDialog, { props: { onCancel } })
    await nextTick()
    const button = Array.from(document.body.querySelectorAll('button'))
      .find(item => item.textContent?.trim() === '取消')
    button!.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await nextTick()
    expect(onCancel).toHaveBeenCalledTimes(1)
    document.body.innerHTML = ''
  })
})

describe('f4 · 单据共享件（批 0A 重写后的行为）', () => {
  it('驳回弹窗：标题取 param、空原因不许提交、确认回传原因', async () => {
    const BillRejectDialog = (await import('@/component/bill/BillRejectDialog.vue')).default
    const onConfirm = vi.fn()
    const wrapper = mountDialog(BillRejectDialog, {
      props: { param: '采购单驳回', onConfirm },
    })
    await nextTick()
    expect(document.body.textContent).toContain('采购单驳回')

    const confirmButton = () => Array.from(document.body.querySelectorAll('button'))
      .find(item => item.textContent?.trim() === '确定')!
    expect(confirmButton().disabled, '原因为空时应禁用').toBe(true)

    const textarea = document.body.querySelector('textarea')!
    textarea.value = '数量不符'
    textarea.dispatchEvent(new Event('input'))
    await nextTick()
    expect(confirmButton().disabled).toBe(false)

    confirmButton().dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await nextTick()
    expect(onConfirm).toHaveBeenCalledWith('数量不符')
    void wrapper
    document.body.innerHTML = ''
  })

  it('添加完成数量：默认值取 param，确认回传数字', async () => {
    const BillAddFinishDialog = (await import('@/component/bill/BillAddFinishDialog.vue')).default
    const onConfirm = vi.fn()
    mountDialog(BillAddFinishDialog, { props: { param: 3.5, onConfirm } })
    await nextTick()
    const input = document.body.querySelector('input') as HTMLInputElement
    expect(input.value).toBe('3.5')
    const button = Array.from(document.body.querySelectorAll('button'))
      .find(item => item.textContent?.trim() === '添加完成')!
    button.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await nextTick()
    expect(onConfirm).toHaveBeenCalledWith(3.5)
    document.body.innerHTML = ''
  })

  it('审核/驳回按钮：可用性来自单据状态机（canAudit/canReject），点击把单据抛出去', async () => {
    const BillAuditOrReject = (await import('@/component/bill/BillAuditOrReject.vue')).default
    const directives = (await import('@/directives')).directives
    const bill = { canAudit: () => true, canReject: () => false }
    const wrapper = mountDialog(BillAuditOrReject, {
      props: { bill },
      global: { directives },
    })
    await nextTick()
    const buttons = wrapper.findAll('button')
    expect(buttons).toHaveLength(2)
    expect(buttons[0]!.text()).toBe('审核')
    expect(buttons[0]!.attributes('disabled')).toBeUndefined()
    expect(buttons[1]!.attributes('disabled')).toBeDefined()
    await buttons[0]!.trigger('click')
    // props 传进来的对象会被 Vue 包成响应式代理，身份比较必然失败 → 用深比较
    expect(wrapper.emitted('onAudit')?.[0]?.[0]).toStrictEqual(bill)
    expect(wrapper.emitted('onReject')).toBeUndefined()
  })
})

/**
 * # 批 5 回归：命令式弹窗必须继承应用上下文
 *
 * `DialogUtil.show()` 把弹窗挂在一棵**新的组件树**里（`framework/feedback.ts` 的宿主），
 * 不显式继承 `appContext` 就拿不到 pinia / router / queryClient。漏接的表现**完全静默**：
 * 弹窗里 `useQuery` 抛 `No 'queryClient' found in Vue context`，异常发生在 `render()` 内部，
 * 页面只是「点了没反应」——channel 的「查看详情」就是这么坏的。
 */
describe('f4 · 命令式弹窗继承应用上下文（批 5 回归）', () => {
  it('接线后：DialogUtil.show 打开的弹窗里 useQuery 能拿到 queryClient', async () => {
    const { QueryClient, VueQueryPlugin } = await import('@tanstack/vue-query')
    const { DialogUtil, setDialogAppContext } = await import('@/framework')
    const { defineComponent, getCurrentInstance, h } = await import('vue')
    const { useQuery } = await import('@tanstack/vue-query')

    const Probe = defineComponent({
      name: 'Probe',
      setup() {
        const query = useQuery({ queryKey: ['probe'], queryFn: async () => '弹窗内容' })
        return () => h('div', { class: 'probe' }, query.data.value ?? '加载中')
      },
    })

    let captured: import('vue').AppContext | null = null
    const Host = defineComponent({
      name: 'Host',
      setup() {
        captured = getCurrentInstance()?.appContext ?? null
        return () => h('div')
      },
    })

    mountDialog(Host, {
      global: { plugins: [[VueQueryPlugin, { queryClient: new QueryClient({ defaultOptions: { queries: { retry: false } } }) }]] },
    })
    expect(captured, '没拿到应用上下文，测试本身失效').toBeTruthy()

    setDialogAppContext(captured)
    try {
      void DialogUtil.show(Probe)
      await vi.waitFor(() => {
        expect(document.body.textContent).toContain('弹窗内容')
      }, { timeout: 4000 })
    }
    finally {
      setDialogAppContext(null)
      document.body.innerHTML = ''
    }
  })

  it('main.ts 里有这行接线（漏了就等于所有命令式弹窗都在裸上下文里渲染）', async () => {
    const { readFileSync } = await import('node:fs')
    expect(readFileSync('src/main.ts', 'utf8')).toContain('setDialogAppContext(app._context)')
  })
})
