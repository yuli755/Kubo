// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { createAppRouter, getRouter, initVueRouter } from '@/router/appRouter'

/**
 * `appRouter` 契约测试：锁「复刻 airpower RouterUtil 的动态菜单路由」关键语义——
 * 标题设置、getRouter 守卫、按菜单 id 去重、redirect 与 component 两种注册分支。
 */
describe('appRouter · RouterUtil 复刻', () => {
  const Dummy = defineComponent({ template: '<div />' })

  it('createAppRouter 后 getRouter 可用', () => {
    const router = createAppRouter([{ path: '/', component: Dummy }])
    expect(getRouter()).toBe(router)
  })

  it('未创建时 getRouter 抛错（独立模块实例）', async () => {
    // 模块级 router 缓存在其它用例已被赋值，须用全新模块实例覆盖未初始化分支
    vi.resetModules()
    const fresh = await import('@/router/appRouter')
    expect(() => fresh.getRouter()).toThrow('请先调用 createAppRouter 创建路由')
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
    expect(router.hasRoute('1')).toBe(true)
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
})
