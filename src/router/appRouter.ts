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

/** 登录后的默认落地地址（受限角色未授权工作台时会被回退到其首个可导航菜单） */
export const DEFAULT_HOME_PATHS = ['/console/index', '/console', '/home'] as const

/**
 * 取菜单树里**第一个能渲染真实页面**的路径（深度优先）。
 *
 * 受限角色（如 WMS 仓储专员）未授权「工作台」时，登录后默认落到 `/console/index` 会命中
 * catch-all 的 404 页（工作台路由没被注册）。这里从已授权菜单里挑一个真实可导航地址作回退，
 * 让登录后看到的不是 404，而是其有权访问的第一个页面。
 *
 * 判定「可导航」与 `initVueRouter.registerRoute` 保持一致：
 * - 带 `redirect` 的节点只是跳板（会再跳到别处），不算真实页面，跳过；
 * - 分组节点（有 children 但无 path）不注册路由，递归进子树找；
 * - 叶子节点（有 path）即真实页面。
 *
 * @param menuList 已授权的菜单树（`/user/getMyMenuList` 下发，`Console.vue` 已持有）
 * @param fallback  整棵树都没有可导航节点时的兜底（默认仍是工作台，至少行为可预期）
 */
export function firstNavigableMenuPath(menuList: MenuItem[], fallback = '/console/index'): string {
  for (const item of menuList) {
    if (item.redirect) {
      continue
    }
    if (item.path && !item.children?.length) {
      return item.path
    }
    if (item.children?.length) {
      const child = firstNavigableMenuPath(item.children, '')
      if (child) {
        return child
      }
    }
  }
  return fallback
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
 * （补上前导斜杠后）拼出的键一致；拼错时组件是 `undefined`，vue-router 会静默渲染空白页，
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
      // 组件路径归一化：`menu.component` 在库里历史上有的带前导斜杠、有的不带（手改/老数据），
      // 不归一化就会拼出 `/src/viewdashboard.vue` 这种**永远匹配不上**的键。
      // 归一化之前，这种菜单会被注册成 `component: undefined` 的路由 —— 匹配成功、渲染空白、
      // 控制台干净（正是下面那道守卫要消灭的症状）；归一化之后它才可能命中真实视图。
      const normalizedPath = componentPath.startsWith('/') ? componentPath : `/${componentPath}`
      const componentKey = `${componentsDirectory}${normalizedPath}.vue`
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
      // 同名路由不重复注册：登录后刷新菜单会再调一次，重复 addRoute 会覆盖已有路由
      if (router.hasRoute(item.id.toString())) {
        return
      }
      if (item.children?.length) {
        registerRoute(item.children)
      }
      if (!item.name) {
        // 库里某个菜单的 name 被清空（管理端表单留空即可造成）原本会 throw ——
        // 后果与成因完全不成比例：一行坏数据让整个控制台进不去（init catch → 错误卡片），
        // 所有用户所有页面全挂。与上面「组件缺失跳过注册」同一哲学：
        // 坏一行、跳一行、留可诊断告警，绝不因单行数据放大成全应用故障。
        // 检查放在 children 递归之后：即使分组节点缺 name，其子树照常注册。
        // 不注册本节点的理由：name 进 meta.name 当页面标题、也是标题守卫的判据，
        // 空标题的路由即使能点也是坏体验。
        console.warn(`[appRouter] 菜单节点缺少 name，已跳过注册（id=${item.id}, path=${item.path}）`)
        return
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
