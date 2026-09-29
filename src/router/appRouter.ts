/**
 * # 应用路由（native vue-router · 2026-09-18 拔掉 airpower 后重写）
 *
 * ## 这里以前为什么是 `RouterUtil` 的薄封装
 * 迁移期 `@airpower/web` 的 dist **内联了一份自己的 vue-router 副本**，于是存在两套注入键：
 * 项目自建 router provide 的是项目那份 vue-router 的 `routerKey`，而所有 airpower 组件
 * （`APanel` 标题、`useLink`、表格内部跳转）inject 的是**它内联副本**的键 —— 结果
 * `inject(routerKey)` 得到 `undefined`，`useRouter().currentRoute` 抛 TypeError，
 * 整页只剩「页面渲染异常」错卡（症状是「预览打不开」，静态检查却全绿）。
 *
 * 因此当时只能让 airpower 创建 router。**现在 airpower 已连根拔除**，`routerKey` 只剩一份，
 * 直接用本仓 `vue-router` 即可 —— 这一层薄封装随之失去意义，故重写为 native 实现。
 *
 * ## 与旧实现逐条对齐的行为（menuNavigation / menuClick / consoleMount 三套 spec 在守）
 * 1. `createRouter({ history: createWebHistory(), routes })` —— 与 `RouterUtil.createRouter` 同款
 *    （history 模式 + nginx SPA fallback，改 hash 会改变所有已发布的菜单地址）；
 * 2. 每次导航后 `window.scrollTo(0, 0)`；
 * 3. 标题守卫 `标题 - Kubo`（旧实现拼的是 `WebConfig.product`，首屏导航时它还没初始化，
 *    会出现「xxx - undefined」；这里固定产品名）；
 * 4. 动态菜单路由：`registerRoute` 的遍历顺序（先子后父）、**用菜单 id 当路由名**、
 *    组件从 `componentsMap` 按 `${componentsDirectory}${component}.vue` 取、
 *    带 `redirect` 的菜单只注册重定向、同名路由不重复注册（`hasRoute(id)`）。
 */
import type { RouteLocationNormalizedLoaded, RouteMeta, Router, RouteRecordRaw } from 'vue-router'
import { reactive } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'

/** 菜单项最小结构（后端 `/user/getMyMenuList` 下发，`MenuEntity` 结构上满足） */
export interface MenuItem {
  id: number
  name: string
  path?: string
  component?: string
  redirect?: string
  /** 图标名（后端下发字符串，由 `@/component/shell/icons` 解析成组件） */
  icon?: string
  /** 排序号（越小越靠前；缺省按 0 处理） */
  orderNo?: number
  /** 菜单权限码（后端下发；`Console` 会把它合并进 `meta.permission` 供守卫/按钮级判断用） */
  permission?: string
  children?: MenuItem[]
  meta?: RouteMeta
}

/** 当前路由实例（`createAppRouter` 成功后才有值） */
let appRouter: Router | null = null

/** 创建路由实例（替代 airpower `RouterUtil.createRouter`） */
export function createAppRouter(routes: RouteRecordRaw[]): Router {
  const router = createRouter({
    history: createWebHistory(),
    routes,
  })
  router.afterEach(() => {
    window.scrollTo(0, 0)
  })
  router.beforeEach((to, _from, next) => {
    if (to.meta.name || to.name) {
      window.document.title = `${to.meta.name || to.name} - Kubo`
    }
    next()
  })
  appRouter = router
  return router
}

/** 获取全局路由实例 */
export function getRouter(): Router {
  if (!appRouter) {
    throw new Error('请先调用 createAppRouter 创建路由')
  }
  return appRouter
}

/**
 * 动态注册菜单路由（替代 airpower `RouterUtil.initVueRouter`）。
 *
 * `componentsMap` 由 `Console.vue` 用 `import.meta.glob` 递归收集 `/src/view` 下所有 `.vue` ——
 * 键是**绝对路径**（`/src/view/system/menu/list.vue`），与后端菜单里的 `component`
 * 拼出的键一致；拼错时组件是 `undefined`，vue-router 会静默渲染空白页，
 * 所以 `menuContract.spec.ts` 里有一条「每个菜单的 component 都能在 map 里找到」的断言，
 * 且这里对缺失键**跳过注册**（落到 404 + 告警），把静态断言管不到的「库里的菜单被手改」
 * 也变成看得见的问题。
 */
export function initVueRouter(
  menuList: MenuItem[],
  componentsMap: Record<string, () => Promise<unknown>>,
  componentsDirectory = '/src/view',
  parentRouter = 'console',
): void {
  const router = getRouter()

  const addRoute = (menu: MenuItem, componentPath?: string) => {
    const routeRawData = {
      path: menu.path as string,
      name: menu.id.toString(),
      meta: {
        ...menu.meta,
        name: menu.name,
        ...(menu.permission ? { permission: menu.permission } : {}),
      },
    }
    if (componentPath) {
      const componentKey = `${componentsDirectory}${componentPath}.vue`
      const loader = componentsMap[componentKey]
      if (!loader) {
        // 菜单指向的视图不存在（改名 / 删除 / 库里手改过 path）：注册一条 component=undefined
        // 的路由会「匹配成功但渲染空白」，用户只看到一片白、控制台一片干净，查不出原因。
        // 宁可不注册 —— 让它落到 catch-all 的 404 页（看得见的问题才是能修的问题），
        // 同时留一条可诊断的告警把菜单名与缺失键都打出来。
        console.warn(`[appRouter] 菜单「${menu.name}」指向的视图缺失：${componentKey}，已跳过注册（该地址将落到 404）`)
        return
      }
      router.addRoute(parentRouter, {
        ...routeRawData,
        component: loader as never,
      })
      return
    }
    if (menu.redirect) {
      router.addRoute(parentRouter, { ...routeRawData, redirect: menu.redirect })
    }
  }

  const registerRoute = (list: MenuItem[]) => {
    list.forEach((item) => {
      if (!item.name) {
        throw new Error('路由初始化失败，缺少参数')
      }
      // 同名路由不重复注册：登录后刷新菜单会再调一次，重复 addRoute 会覆盖已有路由
      if (router.hasRoute(item.id.toString())) {
        return
      }
      if (item.children?.length) {
        registerRoute(item.children)
      }
      if (!item.path) {
        return
      }
      if (item.redirect) {
        addRoute(item)
        return
      }
      const componentPath = item.component || item.path
      if (componentPath) {
        addRoute(item, componentPath)
      }
    })
  }

  registerRoute(menuList)
}

/** 重载当前路由（替代 airpower `RouterUtil.reloadCurrentRoute`） */
export async function reloadCurrentRoute(): Promise<void> {
  const router = getRouter()
  await router.replace(router.currentRoute.value.fullPath)
}

/** 路由尚未创建时的空路由（只读兜底，避免渲染期抛错） */
const EMPTY_ROUTE = {
  path: '',
  fullPath: '',
  name: undefined,
  params: {},
  query: {},
  hash: '',
  meta: {},
  matched: [],
  redirectedFrom: undefined,
} as unknown as RouteLocationNormalizedLoaded

/** 把「读当前路由」的函数包成一个 `useRoute()` 风格的响应式对象 */
function toReactiveRoute(read: () => RouteLocationNormalizedLoaded): RouteLocationNormalizedLoaded {
  return reactive({
    get path() { return read().path },
    get fullPath() { return read().fullPath },
    get name() { return read().name },
    get params() { return read().params },
    get query() { return read().query },
    get hash() { return read().hash },
    get meta() { return read().meta },
    get matched() { return read().matched },
    get redirectedFrom() { return read().redirectedFrom },
  }) as unknown as RouteLocationNormalizedLoaded
}

/**
 * 当前路由（响应式对象）。
 *
 * 保留这个函数而不是直接用 vue-router 的 `useRoute()`：它在**渲染期**被 `ErrorBoundary`
 * 调用（错误页），`useRoute()` 在路由尚未创建时会抛错 —— 那是「错误叠错误」，
 * 会把兜底 UI 自己打崩。这里退化成空路由。
 */
export function useAppRoute(): RouteLocationNormalizedLoaded {
  const router = appRouter
  if (!router) {
    return toReactiveRoute(() => EMPTY_ROUTE)
  }
  return toReactiveRoute(() => router.currentRoute.value as unknown as RouteLocationNormalizedLoaded)
}
