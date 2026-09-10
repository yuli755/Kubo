import type { App } from 'vue'

/**
 * # 全局错误兜底（P1 · 可观测性）
 *
 * 生产级系统的底线之一：未捕获异常不能「静默白屏且无人知晓」。这里把三类错误接到同一入口，
 * 落结构化日志并预留上报钩子（接监控栈时替换 `setErrorReporter` 即可，无需改调用方）。
 *
 * ## 明确不做的事（避免夸大）
 * `app.config.errorHandler` 只**记录**组件渲染/生命周期错误，不会阻止错误沿组件树传播；
 * 真正的「错误边界」（局部降级渲染、不让整页白掉）需要 `onErrorCaptured` + 错误边界组件
 * 包裹路由出口，那是后续演进项，不在这里实现。
 *
 * ## 为什么默认只落 console、不弹 ElMessage
 * 同一坏数据可能让整个列表反复抛错，若每次弹提示会把用户淹没。这里只保证「错误可被观测」，
 * 交互层的友好提示由具体调用点（axios 拦截器、业务方法）负责。
 */

export interface ErrorReport {
  message: string
  stack?: string
  source: 'vue' | 'promise' | 'window'
  time: number
}

export type ErrorReporter = (report: ErrorReport) => void

let reporter: ErrorReporter = (report) => {
  console.error(`[Kubo][global-${report.source}]`, report.message, report.stack ?? '')
}

/** 注入生产上报（Sentry / 自建监控）。默认实现只落 console。 */
export function setErrorReporter(next: ErrorReporter): void {
  reporter = next
}

function toReport(error: unknown, source: ErrorReport['source']): ErrorReport {
  if (error instanceof Error) {
    return { message: error.message, stack: error.stack, source, time: Date.now() }
  }
  return { message: String(error), source, time: Date.now() }
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
