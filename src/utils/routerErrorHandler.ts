import type { Router } from 'vue-router'
import { reportError } from '@/utils/errorHandler'

/**
 * # 路由级错误兜底（错误边界闭环的「导航侧」）
 *
 * `ErrorBoundary.vue` 用 `onErrorCaptured` 兜住**页面渲染**错误，但它管不到**导航过程**里抛的错：
 * 本项目路由组件由 `import.meta.glob` 通配 `src/view` 下的 `.vue` 懒加载（`Console.vue` →
 * `initVueRouter`），发版时常见「旧 index.html 引用已被替换的 chunk」→ 动态 `import()` 失败。
 * 这类错误走 vue-router 的 `router.onError`，**不经过** `onErrorCaptured`，若不注册就会白屏且无恢复手段。
 *
 * 本模块补上这条：注册 `router.onError`，把导航/chunk 加载错误：
 * 1. 以独立 context `router` 上报（复用 `errorHandler` 的 reporter，生产已接 `/api/error-report`）；
 * 2. 命中「chunk 加载失败」特征时**刷新一次**拉最新 `index.html` + 新 chunk hash，帮用户自动恢复。
 *
 * ## 为什么刷新而不是展示重试 UI
 * chunk 加载失败发生在路由进入之前，页面组件根本没渲染出来，`ErrorBoundary` 无从兜底；
 * 此时唯一可靠的恢复是重新拉取入口资源。刷新由 `sessionStorage` 时间窗守卫防风暴
 * （同一会话 10s 内最多刷新一次，避免坏部署把用户困在无限刷新里）。
 */

/** 刷新守卫的 sessionStorage key 与时间窗 */
const RELOAD_FLAG_KEY = 'kubo:router-error-reload'
const RELOAD_WINDOW_MS = 10_000

/**
 * 判断是否「懒加载 chunk 加载失败」类错误（各浏览器报错文案略有差异，按特征匹配）。
 * 抽出为纯函数便于契约测试。
 */
export function isChunkLoadError(error: unknown): boolean {
  const message = error instanceof Error ? error.message : String(error)
  return /failed to fetch dynamically imported module|loading chunk .* failed|importing a module script failed|error loading dynamically imported module/i.test(message)
}

/**
 * 命中 chunk 加载失败时刷新一次（时间窗内不重复刷新）。
 * `reload` 参数默认 `window.location.reload`，抽出为可注入便于契约测试（jsdom 的 location.reload 不可 mock）。
 */
export function reloadToRecover(reload: () => void = () => window.location.reload()): void {
  const last = Number(sessionStorage.getItem(RELOAD_FLAG_KEY) || '0')
  if (Date.now() - last < RELOAD_WINDOW_MS) {
    return
  }
  sessionStorage.setItem(RELOAD_FLAG_KEY, String(Date.now()))
  reload()
}

/**
 * 注册路由级错误兜底（在 `createAppRouter` 之后、`app.mount` 之前调用）。
 *
 * @param router vue-router 实例
 * @param reload 刷新函数（默认 `window.location.reload`，测试注入 mock）
 */
export function installRouterErrorHandler(
  router: Router,
  reload: () => void = () => window.location.reload(),
): void {
  router.onError((error) => {
    reportError(error, 'router')
    if (isChunkLoadError(error)) {
      reloadToRecover(reload)
    }
  })
}
