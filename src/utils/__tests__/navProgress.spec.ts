// @vitest-environment jsdom
import type { Router } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

/**
 * D3 契约：顶部进度条必须**所有出路都收尾**。
 *
 * 钉住的反例来自对标实现（`law-firm-web/src/router/permission.js`）：它只在
 * `afterEach` 与各处 `next('/xxx')` 分支手工 `NProgress.done()`，
 * 守卫**抛错**时 vue-router 走 `router.onError` 而非 `afterEach`
 * —— 漏一条就是进度条永久停在 99% 的死条（比没有进度条更糟，
 * 因为它一直宣称"还在加载"）。这里用真 vue-router 实例把四条出路都跑一遍。
 */

const doneSpy = vi.hoisted(() => vi.fn())
const startSpy = vi.hoisted(() => vi.fn())

vi.mock('nprogress', () => ({
  default: {
    configure: vi.fn(),
    start: startSpy,
    done: doneSpy,
    remove: vi.fn(),
  },
}))

/** 内存路由：三个页面，守卫由调用方在安装进度条之后再挂（与 main.ts 顺序一致） */
function makeRouter() {
  const router = createRouter({
    history: createMemoryHistory(),
    // 裸组件对象即可：路由级导航不渲染组件，不需要 vue 运行时上下文
    routes: [
      { path: '/', component: {} },
      { path: '/a', component: {} },
      { path: '/bad', component: {} },
    ],
  })
  return router
}

/** 安装 D3，返回卸载函数 */
async function install(router: Router) {
  const { installNavProgress, __resetNavProgressForTests } = await import('@/utils/navProgress')
  __resetNavProgressForTests()
  return installNavProgress(router)
}

describe('navProgress · D3 顶部进度条收尾', () => {
  beforeEach(() => {
    startSpy.mockReset()
    doneSpy.mockReset()
  })

  afterEach(async () => {
    const { __resetNavProgressForTests } = await import('@/utils/navProgress')
    __resetNavProgressForTests()
  })

  it('成功导航：start 一次、done 一次', async () => {
    const router = makeRouter()
    await router.push('/')
    const dispose = await install(router)
    startSpy.mockClear()
    doneSpy.mockClear()

    await router.push('/a')
    expect(startSpy).toHaveBeenCalledTimes(1)
    expect(doneSpy).toHaveBeenCalledTimes(1)
    dispose()
  })

  it('守卫抛错（走 onError、不走 afterEach）：仍然 done，不留死条', async () => {
    const router = makeRouter()
    await router.push('/')
    const dispose = await install(router)
    // 与 main.ts 顺序一致：进度条先装、业务守卫后装 ⇒ start 一定先于守卫抛错发生
    router.beforeEach(() => {
      throw new Error('guard exploded')
    })
    startSpy.mockClear()
    doneSpy.mockClear()

    await router.push('/bad').catch(() => {})
    expect(startSpy).toHaveBeenCalledTimes(1)
    // 这一条就是 D3 与对标实现的关键差异：缺 onError 收尾时 done 恒为 0 次
    expect(doneSpy).toHaveBeenCalledTimes(1)
    dispose()
  })

  it('导航被取消（next(false)）：同样 done', async () => {
    const router = makeRouter()
    await router.push('/')
    const dispose = await install(router)
    router.beforeEach((to, _from, next) => {
      if (to.path === '/a') {
        next(false)
        return
      }
      next()
    })
    startSpy.mockClear()
    doneSpy.mockClear()

    await router.push('/a').catch(() => {})
    expect(startSpy).toHaveBeenCalledTimes(1)
    expect(doneSpy).toHaveBeenCalledTimes(1)
    dispose()
  })

  it('重复安装只生效一次（不把守卫装两遍）', async () => {
    const router = makeRouter()
    const dispose = await install(router)
    const { installNavProgress } = await import('@/utils/navProgress')
    installNavProgress(router)

    startSpy.mockClear()
    await router.push('/a')
    expect(startSpy).toHaveBeenCalledTimes(1)
    dispose()
  })

  it('卸载后不再驱动进度条', async () => {
    const router = makeRouter()
    const dispose = await install(router)
    dispose()

    startSpy.mockClear()
    doneSpy.mockClear()
    await router.push('/a')
    expect(startSpy).not.toHaveBeenCalled()
    expect(doneSpy).not.toHaveBeenCalled()
  })

  it('配置：默认无 spinner、minimum 保守（不做「一上来就 30%」的假进度）', async () => {
    const router = makeRouter()
    const dispose = await install(router)
    const { default: NProgress } = await import('nprogress')
    const config = vi.mocked(NProgress.configure).mock.calls.at(-1)?.[0]
    expect(config).toMatchObject({ showSpinner: false, minimum: 0.1 })
    dispose()
  })
})
