/**
 * # 路由级预加载
 *
 * 在用户可能即将访问某个路由前（例如登录成功后、悬停菜单时），
 * 提前触发对应组件 chunk 的下载，使后续导航的加载时间趋近于零。
 *
 * ## 使用方式
 * ```ts
 * import { useRoutePreload } from '@/composables/useRoutePreload'
 *
 * // 预加载单个懒加载组件
 * const { preload } = useRoutePreload()
 * preload(() => import('@/view/home.vue'))
 *
 * // 批量预加载
 * preload([
 *   () => import('@/view/home.vue'),
 *   () => import('@/component/Console.vue'),
 * ])
 * ```
 *
 * 实现说明：直接调用工厂函数即可触发 Vite 的动态 import，
 * 返回的 Promise 解析即代表 chunk 已下载并执行。
 * 多次调用同一工厂函数会被浏览器/运行时去重缓存。
 */
export function useRoutePreload() {
  /** 已预加载的工厂函数集合，避免重复触发 */
  const loaded = new WeakMap<() => Promise<unknown>, boolean>()

  function preload(loaders: (() => Promise<unknown>) | Array<() => Promise<unknown>>): void {
    const list = Array.isArray(loaders) ? loaders : [loaders]
    for (const loader of list) {
      if (loaded.get(loader)) {
        continue
      }
      loaded.set(loader, true)
      // 仅触发下载，错误静默（预加载失败不应阻塞主流程）
      loader().catch(() => {})
    }
  }

  return { preload }
}

/**
 * 预加载组件类型约束（仅用于类型推导，运行时不依赖）
 */
