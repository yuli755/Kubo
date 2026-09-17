import type { App } from 'vue'

/**
 * # 全局错误兜底（P1 · 可观测性）
 *
 * 生产级系统的底线之一：未捕获异常不能「静默白屏且无人知晓」。这里把三类错误接到同一入口，
 * 落结构化日志并预留上报钩子（接监控栈时替换 `setErrorReporter` 即可，无需改调用方）。
 *
 * ## 明确不做的事（避免夸大）
 * `app.config.errorHandler` 只**记录**组件渲染/生命周期错误，不会阻止错误沿组件树传播；
 * 真正的「错误边界」（局部降级渲染、不让整页白掉）由 `onErrorCaptured` + 错误边界组件包裹
 * 路由出口实现，见 `src/component/ErrorBoundary.vue`（已落地，包裹 `App.vue` 的 `<RouterView>`）。
 * 此外，**导航侧**的懒加载 chunk 加载失败走 `router.onError`、不经过 `onErrorCaptured`，
 * 由 `src/utils/routerErrorHandler.ts` 兜底（上报 + 刷新恢复）。
 * 本文件只负责「记录」，边界组件负责「降级」，路由侧兜底负责「导航恢复」，三者分工、互不重复。
 *
 * ## 为什么默认只落 console、不弹 ElMessage
 * 同一坏数据可能让整个列表反复抛错，若每次弹提示会把用户淹没。这里只保证「错误可被观测」，
 * 交互层的友好提示由具体调用点（axios 拦截器、业务方法）负责。
 */

export interface ErrorReport {
  message: string
  stack?: string
  source: 'vue' | 'promise' | 'window' | 'manual'
  /** 业务上下文（如 login / 单据提交 / 查询），帮助定位错误来源 */
  context?: string
  time: number
}

type ErrorReporter = (report: ErrorReport) => void

let reporter: ErrorReporter = (report) => {
  const ctx = report.context ? ` (context=${report.context})` : ''
  console.error(`[Kubo][global-${report.source}]`, report.message, report.stack ?? '', ctx)
}

/** 注入生产上报（Sentry / 自建监控）。默认实现只落 console。 */
export function setErrorReporter(next: ErrorReporter): void {
  reporter = next
}

function toReport(error: unknown, source: ErrorReport['source'], context?: string): ErrorReport {
  if (error instanceof Error) {
    return { message: error.message, stack: error.stack, source, context, time: Date.now() }
  }
  return { message: String(error), source, context, time: Date.now() }
}

/**
 * 业务代码主动上报错误（供 `try/catch` 捕获点调用）。
 *
 * 与 `installGlobalErrorHandler` 的兜底互补：后者接「未捕获」的异常，这里接「已捕获但值得上报」的
 * 错误（登录失败、接口超时、设备数据拉取失败……）。接入后这些错误会走统一的 `reporter`，
 * 将来接 Sentry/自建监控时自动纳入，无需再改调用点。默认实现仍落 console，不改变现有行为。
 */
export function reportError(error: unknown, context?: string): void {
  reporter(toReport(error, 'manual', context))
}

/** 在 main.ts 挂载前调用，统一接住 Vue 组件错误、未处理 rejection 与全局 error。 */
export function installGlobalErrorHandler(app: App): void {
  app.config.errorHandler = (err, _instance, info) => {
    reporter(toReport(err, 'vue'))
    if (info) {
      console.error('[Kubo][vue-error-info]', info)
    }
  }

  window.addEventListener('unhandledrejection', (event) => {
    reporter(toReport(event.reason, 'promise'))
  })

  window.addEventListener('error', (event) => {
    reporter({
      message: event.message || `resource-load-error: ${event.target ?? ''}`,
      source: 'window',
      time: Date.now(),
    })
  })
}
