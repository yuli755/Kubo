/**
 * # 应用路由（复刻 airpower RouterUtil 的动态菜单路由）
 *
 * 首屏（main.ts）不再静态引入 `@airpower/web`，故无法使用 airpower 的 `RouterUtil`
 * （其 `router` 为 airpower 内部静态属性，须经 `RouterUtil.createRouter` 初始化）。
 *
 * 本模块以 vue-router 原生 API 复刻 `RouterUtil` 的完整语义（已对照 airpower dist 源码
 * 逐行核验）：
 *
 *   - `createAppRouter`   = `RouterUtil.createRouter`：createWebHistory + afterEach 滚动复位
 *     + beforeEach 设标题（`路由名 - Kubo`），并缓存全局 router 实例。
 *   - `initVueRouter`     = `RouterUtil.initVueRouter`：缓存 `import.meta.glob` 组件映射，
 *     遍历菜单递归 `addRoute`（airpower 原实现有 `if (!WebConfig.isTimeout)` 短路，默认
 *     false，等价于直接注册）。
 *   - `registerRoute`     = `RouterUtil.registerRoute`：按菜单 id 去重、先递归子菜单、
 *     再按 path/redirect/component 注册。
 *   - `reloadCurrentRoute`= `RouterUtil.reloadCurrentRoute`：`replace(fullPath)`。
 *   - `getRouter`         替代 `RouterUtil.router` 静态属性。
 *
 * 用结构类型 `MenuItem`（而非 MenuEntity）避免本模块引入 airpower 依赖链，
 * 使 main.ts 可安全引入 `createAppRouter` 而不拖入 airpower 单体。
 */
import type { RouteComponent, RouteMeta, Router, RouteRecordRaw } from 'vue-router'
import { createRouter, createWebHistory } from 'vue-router'

let router: Router | null = null
let components: Record<string, () => Promise<unknown>> = {}
let componentsDirectory = '/src/view'

/** 菜单项最小结构（复刻 airpower registerRoute 所需字段，MenuEntity 结构上满足） */
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
  children?: MenuItem[]
  meta?: RouteMeta
}

/** 创建路由实例（复刻 RouterUtil.createRouter） */
export function createAppRouter(routes: RouteRecordRaw[]): Router {
  router = createRouter({
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
  return router
}

/** 获取全局路由实例（替代 RouterUtil.router） */
export function getRouter(): Router {
  if (!router) {
    throw new Error('请先调用 createAppRouter 创建路由')
  }
  return router
}

function addRoute(parentRouter: string, menu: MenuItem, componentPath?: string) {
  const r = getRouter()
  const base = {
    path: menu.path as string,
    name: menu.id.toString(),
    meta: { ...menu.meta, name: menu.name },
  }
  if (componentPath) {
    const component = components[`${componentsDirectory}${componentPath}.vue`]
    r.addRoute(parentRouter, {
      ...base,
      component: component as unknown as RouteComponent,
    } as RouteRecordRaw)
    return
  }
  if (menu.redirect) {
    r.addRoute(parentRouter, {
      ...base,
      redirect: menu.redirect,
    } as RouteRecordRaw)
  }
}

function registerRoute(menuList: MenuItem[], parentRouter: string) {
  const r = getRouter()
  menuList.forEach((item) => {
    if (!item.name) {
      throw new Error('路由初始化失败，缺少参数')
    }
    if (r.hasRoute(item.id.toString())) {
      return
    }
    if (item.children && item.children.length > 0) {
      registerRoute(item.children, parentRouter)
    }
    if (!item.path) {
      return
    }
    if (item.redirect) {
      addRoute(parentRouter, item)
      return
    }
    const componentPath = item.component || item.path
    if (componentPath) {
      addRoute(parentRouter, item, componentPath)
    }
  })
}

/** 动态注册菜单路由（复刻 RouterUtil.initVueRouter） */
export function initVueRouter(
  menuList: MenuItem[],
  componentsMap: Record<string, () => Promise<unknown>>,
  dir = '/src/view',
  parentRouter = 'console',
) {
  components = componentsMap
  componentsDirectory = dir
  registerRoute(menuList, parentRouter)
}

/** 重载当前路由（复刻 RouterUtil.reloadCurrentRoute） */
export async function reloadCurrentRoute() {
  const r = getRouter()
  await r.replace(r.currentRoute.value.fullPath)
}
