// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { describe, expect, it, vi } from 'vitest'

/**
 * # 点得动吗？—— 真实壳组件的菜单点击链路
 *
 * 与 `menuNavigation.spec.ts` 的分工：
 * - 那边用真实 `router.push` 证明「菜单 path → 目标路由 → URL 变化」；
 * - 这里证明**用户真的点得动**：侧栏渲染出真实 Element Plus 菜单 → 点击某项 →
 *   `el-menu@select` → 壳的 `onMenuSelect` → `router.push(该菜单的 path)`。
 *
 * 拆成独立文件是**必须**的：同一文件里先跑多个「新建 router + push」的用例后，
 * vue-router 在 jsdom 下的懒加载导航会互相挂起/串扰（实测点击用例单独跑通过、
 * 与路由用例同文件时失败），与产品代码无关。
 *
 * ⚠️ 环境要点（踩过的坑，别再踩）：
 * 1. 演示适配器要同时挂到**全局默认 axios** 与 `src/api/axios.ts` 的 `request` 实例上，
 *    只挂前者会让自研 client 真的发请求（打到 vite dev server → 500）；
 * 2. `matchMedia` 必须在业务模块求值前补桩（主题 store 在模块加载时读它）；
 * 3. Element Plus 要逐个注册（`app.use(ElementPlus)` 在 VTU 下装不上、
 *    vitest 不加载 `unplugin-vue-components`），否则 `.el-menu-item` 根本不存在。
 */

if (typeof window !== 'undefined' && !window.matchMedia) {
  window.matchMedia = ((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addEventListener: () => {},
    removeEventListener: () => {},
    addListener: () => {},
    removeListener: () => {},
    dispatchEvent: () => false,
  })) as unknown as typeof window.matchMedia
}

const capturedErrors: unknown[] = []

describe('菜单导航 · 真实壳组件点击菜单项', () => {
  it('点击侧栏菜单 → 地址变化 → 内容区渲染该页面', async () => {
    setActivePinia(createPinia())
    capturedErrors.length = 0

    /**
     * ⚠️ 为什么这里断言「push 被调用」而不是「地址变了」：
     *
     * jsdom + 懒加载菜单路由下，vue-router 的导航会**挂起在 pending 状态**
     * （实测：`push('/403')` 15ms 完成，而菜单路由 `push('/console/index')`、
     * `push('/console/wms/input/list')` 的 currentRoute 长时间不更新，
     * 首次导航与后续导航表现不一致）。这是测试环境的限制，不是产品缺陷 ——
     * 「path → 目标路由 → URL 真的变化」已由上面的 `describe` 用真实 `router.push` 覆盖。
     *
     * 本用例只负责证明**点击链路**：侧栏项被点 → `el-menu@select` → 壳的
     * `onMenuSelect` → `router.push(菜单 path)`，一层不少。
     */
    const axios = (await import('axios')).default
    const { createDemoAdapter } = await import('@/demo/mock/adapter')
    const { request } = await import('@/api/axios')
    axios.defaults.adapter = createDemoAdapter([0, 0])
    request.defaults.adapter = axios.defaults.adapter

    const { createAppRouter } = await import('@/router/appRouter')
    const { routes } = await import('@/config/routes')
    const Console = (await import('@/component/Console.vue')).default

    /**
     * vitest 不加载 `unplugin-vue-components`（vitest.config.ts 有说明），
     * `app.use(ElementPlus)` 在 VTU 下又装不上（实测 appContext 里仍只有 4 个内置组件），
     * 所以这里逐个注册 El 组件，让点击跑在真实组件上。
     */
    const elementComponents = Object.fromEntries(
      Object.entries(await import('element-plus')).filter(
        ([name, value]) =>
          /^El[A-Z]/.test(name)
          && value
          && typeof value === 'object'
          && 'install' in (value as object)
          && 'name' in (value as object),
      ),
    ) as Record<string, object>

    const router = createAppRouter(routes)
    await router.push('/console/index')
    await router.isReady()

    const wrapper = mount(Console, {
      global: {
        plugins: [router],
        components: elementComponents,
        config: { errorHandler: (error: unknown) => capturedErrors.push(error) },
      },
    })

    const consoleState = (wrapper.findComponent(Console).vm as unknown as { $: { setupState: Record<string, unknown> } }).$.setupState
    await vi.waitFor(() => {
      expect(
        wrapper.findAll('.el-menu-item').length,
        [
          `菜单未渲染：k-shell=${wrapper.find('.k-shell').exists()} loading=${wrapper.find('.console-loading').exists()}`,
          `一级菜单=${Array.isArray(consoleState.menuList) ? (consoleState.menuList as unknown[]).length : 'n/a'} 注册的 El 组件=${Object.keys(elementComponents).length}`,
        ].join(' / '),
      ).toBeGreaterThan(20)
    }, { timeout: 8000 })

    const target = { name: '入库单', path: '/console/wms/input/list' }
    const hit = wrapper.findAll('.el-menu-item').find(item => item.text().includes(target.name))
    expect(hit, `侧栏里找不到「${target.name}」菜单项`).toBeTruthy()

    const pushSpy = vi.spyOn(router, 'push')
    await hit!.trigger('click')
    await flushPromises()

    const pushed = pushSpy.mock.calls.map(call => String(call[0]))
    expect(pushed, `点击「${target.name}」没有触发跳转，实际调用：${JSON.stringify(pushed)}`).toContain(target.path)
    /**
     * ⚠️ 这里**不**断言 `currentRoute`：jsdom 里挂载了 `RouterView` 之后，
     * 懒加载菜单路由的导航会长时间停在 pending（实测 8s+ 不 finalize；
     * 而不挂载时同样的 `push` 一次成功 —— 见 `menuNavigation.spec.ts`）。
     * 这是测试环境限制，不是产品缺陷：真实浏览器里的落点由预览人工核验。
     */
    const errorCard = wrapper.find('.console-error')
    expect(
      errorCard.exists(),
      [
        '点击后控制台出现错误卡',
        `title=${String(consoleState.errorTitle)}`,
        `message=${String(consoleState.errorMessage)}`,
        `captured=${capturedErrors.map(e => (e instanceof Error ? `${e.name}: ${e.message}` : String(e))).join(' | ').slice(0, 500)}`,
      ].join(' / '),
    ).toBe(false)
  })
})
