/**
 * # 应用路由（airpower `RouterUtil` 的薄封装）
 *
 * ## ⚠️ 为什么**不能**用 vue-router 原生 API 自己复刻 `RouterUtil`
 *
 * 本文件曾经为了「首屏不引入 airpower 单体」而用 vue-router 原生 API 复刻过 `RouterUtil`
 * （语义逐行对齐、typecheck/lint 全绿），但它在**运行时是致命的**：
 *
 * `@airpower/web` 的 dist **内联了一份自己的 vue-router 副本**（见其 `dist/main.js`
 * 中 `routerKey = Symbol("router")` 的独立定义与 `app.provide(routerKey, router)`），
 * 于是存在**两套注入键**：
 *
 * | 谁 | provide 的键 | 谁在 inject |
 * | --- | --- | --- |
 * | 项目自建 router（`vue-router` 包） | 项目这份 vue-router 的 `routerKey` | 项目自己的 `useRoute()` |
 * | airpower 内联副本 | airpower 副本里的 `routerKey` | **所有 airpower 组件**（`APanel` 标题、`useLink`、菜单/表格内部跳转…） |
 *
 * 结果是 airpower 组件 `inject(routerKey)` 得到 `undefined` →
 * `useRouter().currentRoute` 抛 `TypeError`（`APanel` 计算标题时必踩）→ 异常冒泡到
 * `Console.vue` 的 `onErrorCaptured` → 整页只剩「**页面渲染异常**」错卡。
 * 症状是「预览打不开、看不到任何效果」，而静态检查全绿。
 *
 * ## ✅ 正确做法
 * 路由实例**必须由 airpower 创建**（`RouterUtil.createRouter`），这样 `app.use(router)`
 * 时 provide 的就是 airpower 副本的注入键，airpower 组件才注入得到；
 * 项目侧只做一层薄封装，不再自己造 router。
 *
 * ## 代价（已知并接受）
 * 静态引入 `RouterUtil` 会把 airpower 单体带进首屏 chunk（`pnpm size` 实测仍在预算内）。
 * 这是**必付成本**：airpower 组件是存量 144 个页面的基础组件，其标题/跳转都依赖路由注入。
 * 想省这 80KB 就得改 airpower 组件本身（不可行），或让所有页面放弃 airpower 组件（等于重写前端）。
 *
 * ## `useAppRoute()` 存在的理由
 * 项目侧代码原来 `import { useRoute } from 'vue-router'` —— 那读的是**项目那份**的
 * `routeLocationKey`，同样注入不到（返回 `undefined`）。这里提供 `useAppRoute()` 作为替代：
 * 返回一个**响应式**的路由对象（getter 指向 `router.currentRoute`），API 与 `useRoute()` 一致，
 * 业务代码 `route.query.xxx` / `route.path` 的写法无需改动。
 */
import type { RouteLocationNormalizedLoaded, RouteMeta, Router, RouteRecordRaw } from 'vue-router'
import { RouterUtil, WebConfig } from '@airpower/web'
import { reactive } from 'vue'

/** 菜单项最小结构（airpower `registerRoute` 所需字段，`MenuEntity` 结构上满足） */
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

/**
 * 创建路由实例（airpower `RouterUtil.createRouter`）。
 *
 * `ignoreGuard = true`：跳过 airpower 自带的标题守卫 —— 它拼的是 `WebConfig.product`，
 * 而 `WebConfig` 要等业务页（`airpower-bootstrap`）才初始化，首屏导航时还是 undefined，
 * 会出现「xxx - undefined」的标题。这里改为自己注册同语义的守卫（产品名固定 Kubo）。
 */
export function createAppRouter(routes: RouteRecordRaw[]): Router {
  const router = RouterUtil.createRouter(routes, true)
  router.beforeEach((to, _from, next) => {
    if (to.meta.name || to.name) {
      window.document.title = `${to.meta.name || to.name} - Kubo`
    }
    next()
  })
  return router
}

/** 获取全局路由实例（airpower 的 `RouterUtil.router` 静态属性） */
export function getRouter(): Router {
  if (!RouterUtil.router) {
    throw new Error('请先调用 createAppRouter 创建路由')
  }
  return RouterUtil.router
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
 * 当前路由（响应式对象，替代 `vue-router` 的 `useRoute()`）。
 *
 * 与 `useRoute()` 的差异：只暴露路由位置字段（path/query/params/meta/matched…），
 * 不含 `useRoute()` 附带的内部属性；项目内用法（读 query、判当前路径、拼面包屑）均已覆盖。
 *
 * ⚠️ 路由尚未创建时返回**空路由**而不是抛错：本函数的调用点在渲染期
 * （如 `ErrorBoundary` 展示错误页），在渲染期抛错会「错误叠错误」、把兜底 UI 自己打崩。
 */
export function useAppRoute(): RouteLocationNormalizedLoaded {
  const router = RouterUtil.router
  if (!router) {
    return toReactiveRoute(() => EMPTY_ROUTE)
  }
  return toReactiveRoute(() => router.currentRoute.value as unknown as RouteLocationNormalizedLoaded)
}

/** 动态注册菜单路由（airpower `RouterUtil.initVueRouter`） */
export function initVueRouter(
  menuList: MenuItem[],
  componentsMap: Record<string, () => Promise<unknown>>,
  dir = '/src/view',
  parentRouter = 'console',
) {
  // ⚠️ 必须先复位 `WebConfig.isTimeout` 再注册路由：airpower 的 `initVueRouter()` 在
  // `isTimeout === true` 时**直接跳过整个动态路由注册**，且没有任何地方会把它复位。
  // 该标志原本只由 404 兜底页置真（已修），但为防存量构建/第三方写入，这里每次初始化
  // 都显式复位一次 —— 否则「点任何菜单都进 404」且刷新也难自愈。
  //
  // 放在这里而不是 `Console.vue`：这个 WebConfig 只被下面的 airpower 调用消费，
  // 应用外壳不该为了别人的标志位 import 一个库。`appRouter` 本身是 F5 的拆除对象
  // （要等 airpower 组件全部退场才能换掉 `RouterUtil`，见 F4 文档批 0 说明）。
  WebConfig.isTimeout = false
  // airpower 的入参类型是它自己的 `IMenu & RootEntity`；项目用结构类型 MenuItem，
  // 运行期字段一致（id/name/path/component/redirect/children），故此处断言收敛在边界上。
  RouterUtil.initVueRouter(menuList as never, componentsMap, dir, parentRouter)
}

/** 重载当前路由（airpower `RouterUtil.reloadCurrentRoute`） */
export async function reloadCurrentRoute() {
  await RouterUtil.reloadCurrentRoute()
}
