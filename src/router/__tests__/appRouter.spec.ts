// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { createAppRouter, getRouter, initVueRouter } from '@/router/appRouter'

/**
 * `appRouter` 契约测试：锁关键语义 —— 标题设置、`getRouter` 守卫、按菜单 id 去重、
 * redirect 与 component 两种注册分支、以及「缺 name 抛错」。
 *
 * 实现是 native `vue-router`（`RouterUtil` 那层薄封装已随 airpower 拔除，见 appRouter.ts 文件头），
 * 模块内的 router 是**闭包私有变量**，外部改不到 —— 所以「未创建路由」这条分支用
 * `vi.resetModules()` 拿一份全新模块实例来测（旧实现在这里要手动改 `RouterUtil.router`）。
 */
describe('appRouter · native vue-router 实现', () => {
  const Dummy = defineComponent({ template: '<div />' })

  it('createAppRouter 后 getRouter 可用', () => {
    const router = createAppRouter([{ path: '/', component: Dummy }])
    expect(getRouter()).toBe(router)
  })

  it('beforeEach 按 meta.name 设置标题', async () => {
    const router = createAppRouter([
      { path: '/', component: Dummy },
      { path: '/home', name: 'home', meta: { name: '首页' }, component: Dummy },
    ])
    await router.push('/home')
    await router.isReady()
    expect(document.title).toBe('首页 - Kubo')
  })

  it('initVueRouter 注册 component 与 redirect 两种菜单', async () => {
    const router = createAppRouter([
      { path: '/', component: Dummy },
      { path: '/console', name: 'console', component: Dummy },
    ])
    const componentsMap = {
      '/src/view/dashboard.vue': () => Promise.resolve(Dummy),
    }
    initVueRouter(
      [
        { id: 1, name: '仪表盘', path: '/dashboard', component: 'dashboard' },
        { id: 2, name: '跳转', path: '/old', redirect: '/dashboard' },
      ],
      componentsMap,
    )
    expect(router.hasRoute('1')).toBe(true)
    expect(router.hasRoute('2')).toBe(true)
  })

  it('initVueRouter 把菜单权限码写进 meta.permission（守卫按它拦页）', async () => {
    const router = createAppRouter([
      { path: '/', component: Dummy },
      { path: '/console', name: 'console', component: Dummy },
    ])
    initVueRouter(
      [{ id: 9, name: '日志', path: '/log', component: 'log', permission: 'System_Log_list' }],
      { '/src/view/log.vue': () => Promise.resolve(Dummy) },
    )
    const route = router.getRoutes().find(r => r.name === '9')
    expect(route?.meta.permission).toBe('System_Log_list')
  })

  it('initVueRouter 按菜单 id 去重（重复菜单不重复注册）', async () => {
    const router = createAppRouter([
      { path: '/', component: Dummy },
      { path: '/console', name: 'console', component: Dummy },
    ])
    const componentsMap = {
      '/src/view/dashboard.vue': () => Promise.resolve(Dummy),
    }
    initVueRouter(
      [
        { id: 1, name: '仪表盘', path: '/dashboard', component: 'dashboard' },
        { id: 1, name: '仪表盘(重复)', path: '/dashboard2', component: 'dashboard' },
      ],
      componentsMap,
    )
    expect(router.getRoutes().filter(r => r.name === '1')).toHaveLength(1)
  })

  it('initVueRouter 缺 name 抛错', () => {
    const router = createAppRouter([
      { path: '/', component: Dummy },
      { path: '/console', name: 'console', component: Dummy },
    ])
    expect(() => initVueRouter([{ id: 1, name: '', path: '/x', component: 'x' }], {}))
      .toThrow('路由初始化失败，缺少参数')
    void router
  })

  // 放最后：`resetModules` 会让后续 `import` 拿到**新的模块实例**，
  // 前面那些用例依赖模块内 router 被创建过（同一个闭包），顺序反过来会互相干扰。
  it('未创建时 getRouter 抛错', async () => {
    vi.resetModules()
    const fresh = await import('@/router/appRouter')
    expect(() => fresh.getRouter()).toThrow('请先调用 createAppRouter 创建路由')
  })
})
