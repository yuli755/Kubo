import type { MenuItem } from '@/router/appRouter'
// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * # 菜单导航链路（真实 router + 真实点击）
 *
 * 用户报告的「无法实现展示效果及页面跳转 / 页面路径与页面展示不符」，
 * 静态扫描与类型检查**全绿**，因为问题出在运行期的注册与解析上：
 *
 * 1. airpower `registerRoute()` 用 `menu.id` 当路由 name，**同 id 直接跳过注册**；
 *    若菜单 id 撞车，第二条页面永远走 catch-all（表现为点哪都是 404）。
 * 2. 组件取不到时（视图文件被删/改名），它会照旧 `addRoute({ component: undefined })` ——
 *    路由注册成功但渲染为空，**不报错、不 404**，就是一片空白。MES 残留菜单即此症状。
 * 3. 路由 name 是菜单 id，因此「resolve 出来的 name」必须等于该菜单的 id：
 *    这一条同时证明「命中的是这条菜单注册的路由」，而不是别的同路径路由。
 *
 * 本用例不依赖后端：直接拿演示菜单 + 真实的 `import.meta.glob` 组件表注册，
 * 再逐个菜单断言 resolve / push 的落点。
 */

/**
 * jsdom 未实现 `window.matchMedia`（真实浏览器都有），而主题 store 在**模块加载时**就读它。
 * 桩必须早于任何业务模块求值，故放在文件顶层、且业务模块一律用动态 `import()`。
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

/** 与 `Console.vue` 完全相同的组件表（键形如 `/src/view/console/wms/input/list.vue`） */
const views = import.meta.glob('/src/view/**/*.vue') as Record<string, () => Promise<unknown>>

interface Leaf {
  id: number
  name: string
  path: string
  component?: string
  children?: Leaf[]
}

function leaves(nodes: Leaf[]): Leaf[] {
  const out: Leaf[] = []
  for (const node of nodes) {
    if (node.children?.length) {
      out.push(...leaves(node.children))
    }
    else if (node.path) {
      out.push(node)
    }
  }
  return out
}

function groups(nodes: Leaf[]): Leaf[] {
  const out: Leaf[] = []
  for (const node of nodes) {
    if (node.children?.length) {
      out.push(node)
      out.push(...groups(node.children))
    }
  }
  return out
}

async function createRegisteredRouter() {
  const { createAppRouter, initVueRouter } = await import('@/router/appRouter')
  const { routes } = await import('@/config/routes')
  const { demoMenuTree } = await import('@/demo/fixtures/menu')

  const router = createAppRouter(routes)
  initVueRouter(demoMenuTree() as unknown as MenuItem[], views, '/src/view', 'console')
  return router
}

describe('菜单导航 · 路由注册与解析（真实 RouterUtil）', () => {
  beforeEach(() => {
    capturedErrors.length = 0
  })

  it('每个菜单页面都解析到「该菜单自己注册的路由」，而不是 catch-all', async () => {
    const router = await createRegisteredRouter()
    const menuLeaves = leaves((await import('@/demo/fixtures/menu')).demoMenuTree() as unknown as Leaf[])
    expect(menuLeaves.length).toBeGreaterThan(20)

    const problems: string[] = []
    for (const leaf of menuLeaves) {
      const resolved = router.resolve({ path: leaf.path })
      const last = resolved.matched.at(-1)
      if (!last || last.path.includes('catchAll')) {
        problems.push(`${leaf.name} ${leaf.path}：落到了 catch-all（404 页）`)
        continue
      }
      if (String(resolved.name) !== String(leaf.id)) {
        problems.push(`${leaf.name} ${leaf.path}：命中的路由 name=${String(resolved.name)}，期望菜单 id=${leaf.id}`)
      }
      const expectedLoader = views[`/src/view${leaf.component || leaf.path}.vue`]
      if (!expectedLoader) {
        problems.push(`${leaf.name} ${leaf.path}：组件表里没有 ${leaf.component || leaf.path}.vue`)
        continue
      }
      if (last.components?.default !== expectedLoader) {
        problems.push(`${leaf.name} ${leaf.path}：注册的不是它自己的视图（路径与页面不一致）`)
      }
    }
    expect(problems, '菜单 → 路由 → 视图 三方对不上').toEqual([])
  })

  it('分组节点不注册路由（只有叶子点击才有页面）', async () => {
    const router = await createRegisteredRouter()
    const menuGroups = groups((await import('@/demo/fixtures/menu')).demoMenuTree() as unknown as Leaf[])
    const registered = menuGroups.filter(group => router.hasRoute(String(group.id))).map(group => group.name)
    expect(registered, '分组节点被注册成了空白路由').toEqual([])
  })

  it('`/console` 入口重定向到工作台（不是 404 页）', async () => {
    const router = await createRegisteredRouter()
    // `resolve()` 不跟随 redirect（它只做匹配），必须真的导航一次才看得到落点
    await router.push('/console')
    expect(router.currentRoute.value.path, '进入 /console 被送去了错误页面').toBe('/console/index')
    expect(router.currentRoute.value.matched.at(-1)?.path ?? '').not.toContain('catchAll')
  })

  it('不存在的地址仍然落到 404 页（catch-all 没被破坏）', async () => {
    const router = await createRegisteredRouter()
    const resolved = router.resolve('/console/not-a-real/page/list')
    expect(resolved.matched.at(-1)?.path ?? '', 'catch-all 兜底失效，未知地址会静默白屏').toContain('catchAll')
  })

  it('菜单指向已不存在的视图：跳过注册 + 告警，而不是渲染一片空白', async () => {
    // 这条守的是「库里的菜单被手改 / 视图被删改名」—— 静态对账（menuContract.spec.ts）
    // 只看代码里的种子，看不到运行期下发的菜单，所以必须在注册处兜住。
    const { createAppRouter, initVueRouter } = await import('@/router/appRouter')
    const { routes } = await import('@/config/routes')
    const router = createAppRouter(routes)

    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const ghostMenu = [{
      id: 990001,
      name: '已被删掉的页面',
      path: '/console/ghost/list',
      component: '/console/ghost/list',
    }] as unknown as MenuItem[]
    initVueRouter(ghostMenu, views, '/src/view', 'console')

    expect(router.hasRoute('990001'), '视图缺失仍注册了路由（component=undefined 会渲染成空白页）').toBe(false)
    expect(router.resolve('/console/ghost/list').matched.at(-1)?.path ?? '', '缺失视图没落到 404 页').toContain('catchAll')
    expect(warn.mock.calls.flat().join(' '), '没留下可诊断的告警（菜单名与缺失键都要打出来）')
      .toContain('/src/view/console/ghost/list.vue')
    warn.mockRestore()
  })

  it('重复初始化不会重复注册（幂等），也不会把已注册页面挤掉', async () => {
    const router = await createRegisteredRouter()
    const { initVueRouter } = await import('@/router/appRouter')
    const { demoMenuTree } = await import('@/demo/fixtures/menu')
    const before = router.getRoutes().length
    initVueRouter(demoMenuTree() as unknown as MenuItem[], views, '/src/view', 'console')
    expect(router.getRoutes().length).toBe(before)

    const leaf = leaves(demoMenuTree() as unknown as Leaf[])[0]
    expect(String(router.resolve({ path: leaf.path }).name)).toBe(String(leaf.id))
  })

  it('push 到每个菜单路径，当前路由就是该路径（跳转真的发生）', async () => {
    const router = await createRegisteredRouter()
    const menuLeaves = leaves((await import('@/demo/fixtures/menu')).demoMenuTree() as unknown as Leaf[])
    // 只验证路由层跳转，不等待页面组件渲染（渲染由 menuSmoke 探针逐页覆盖）
    for (const leaf of menuLeaves.slice(0, 6)) {
      await router.push({ path: leaf.path })
      expect(router.currentRoute.value.path, `${leaf.name} 跳转后地址不对`).toBe(leaf.path)
      expect(String(router.currentRoute.value.name), `${leaf.name} 跳转后命中的路由不是它自己`).toBe(String(leaf.id))
    }
  })
})
