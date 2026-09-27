/**
 * # airpower vue-router 注入桥接（FIX-G1 项目侧）
 *
 * airpower 组件（Menu/Panel/ChildMenu）内部通过其 bundle 私有的
 * `inject(routerKey)` / `inject(routeLocationKey)` 读取路由。Symbol 唯一性由语言保证，
 * 项目侧「新建一个同名描述符的 Symbol」无法命中——因此这些 Symbol 常量已被
 * `infra/plugins/airpower-router-bridge.ts` 在 transform 期替换为挂在
 * `globalThis.__KUBO_AP_KEYS__` 上的稳定实例；本模块取回**同一组** Symbol，
 * 把项目 router 按 vue-router `install()` 的原生语义 provide 给 app，
 * 使 airpower 组件拿到真实路由而不是 undefined。
 *
 * 注意：本模块只依赖 vue / vue-router，**不得** import `@airpower/web`
 * （首屏不加载 airpower 107 KB 单体的约束，见 airpower-bootstrap.ts）。
 */
import type { App } from 'vue'
import type { Router } from 'vue-router'
import { shallowReactive } from 'vue'

interface ApKeyStore {
  routerKey: symbol
  routeLocationKey: symbol
  routerViewLocationKey: symbol
}

/** 读取（或抢先创建）与插件 transform 相同的 globalThis 稳定 Symbol 组 */
function apKeys(): ApKeyStore {
  const g = globalThis as typeof globalThis & { __KUBO_AP_KEYS__?: Partial<ApKeyStore> }
  const store = (g.__KUBO_AP_KEYS__ ??= {})
  store.routerKey ??= Symbol('router')
  store.routeLocationKey ??= Symbol('route location')
  store.routerViewLocationKey ??= Symbol('router view location')
  return store as ApKeyStore
}

/**
 * 在 `app.use(router)` 之后、`app.mount()` 之前调用一次。
 *
 * 完全对齐 vue-router 安装期 provide 的三件套（对照 airpower dist 内嵌 vue-router
 * 的 `install()` 实现）：
 *  - routerKey             → router 实例（useRouter() 返回值）
 *  - routeLocationKey      → 可枚举 getter 的响应式当前路由（useRoute() 返回值）
 *  - routerViewLocationKey → currentRoute ref（airpower 内嵌 RouterView 消费）
 */
export function provideAirpowerRouter(app: App, router: Router): void {
  const keys = apKeys()
  const currentRoute = router.currentRoute

  // 复刻 vue-router install(): 以初始路由的可枚举键建 getter 代理，包 shallowReactive
  const reactiveRoute: Record<string, unknown> = {}
  for (const keyofRoute in currentRoute.value) {
    Object.defineProperty(reactiveRoute, keyofRoute, {
      get: () => (currentRoute.value as unknown as Record<string, unknown>)[keyofRoute],
      enumerable: true,
    })
  }

  app.provide(keys.routerKey, router)
  app.provide(keys.routeLocationKey, shallowReactive(reactiveRoute))
  app.provide(keys.routerViewLocationKey, currentRoute)
}
