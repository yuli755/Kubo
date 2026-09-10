// @vitest-environment jsdom
import type { App } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { installGlobalErrorHandler, reportError, setErrorReporter } from '@/utils/errorHandler'

/**
 * `installGlobalErrorHandler` 契约测试（P1 · 前端可观测性兜底）。
 *
 * 锁住一条底线：未捕获异常必须「有日志、可上报」，不能静默白屏。这里只锁行为契约
 * （三类入口都接到 reporter、reporter 可替换），不锁具体上报实现（那是监控栈接入时的事）。
 */

function mockApp(): App {
  return { config: {} } as unknown as App
}

describe('errorHandler · 全局错误兜底', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('installGlobalErrorHandler 后，Vue 组件错误会交给当前 reporter', () => {
    const reporter = vi.fn()
    setErrorReporter(reporter)

    const app = mockApp()
    installGlobalErrorHandler(app)

    const err = new Error('render boom')
    app.config.errorHandler!(err, null, 'setup')

    expect(reporter).toHaveBeenCalledTimes(1)
    expect(reporter.mock.calls[0][0]).toMatchObject({
      message: 'render boom',
      stack: err.stack,
      source: 'vue',
    })
  })

  it('reporter 可替换：setErrorReporter 后新 reporter 生效', () => {
    const first = vi.fn()
    const second = vi.fn()
    setErrorReporter(first)
    const app = mockApp()
    installGlobalErrorHandler(app)

    setErrorReporter(second)
    app.config.errorHandler!(new Error('x'), null, 'render')

    expect(first).not.toHaveBeenCalled()
    expect(second).toHaveBeenCalledTimes(1)
  })

  it('非 Error 异常也会被上报（转成 String，不丢信息）', () => {
    const reporter = vi.fn()
    setErrorReporter(reporter)
    const app = mockApp()
    installGlobalErrorHandler(app)

    app.config.errorHandler!('plain string error', null, 'setup')

    expect(reporter).toHaveBeenCalledTimes(1)
    expect(reporter.mock.calls[0][0].message).toBe('plain string error')
  })

  it('注册了 unhandledrejection 与 window error 两个全局监听', () => {
    const addSpy = vi.spyOn(window, 'addEventListener')
    installGlobalErrorHandler(mockApp())

    const events = addSpy.mock.calls.map(c => c[0])
    expect(events).toContain('unhandledrejection')
    expect(events).toContain('error')
  })

  it('reportError 手动上报走同一 reporter，并带 context', () => {
    const reporter = vi.fn()
    setErrorReporter(reporter)

    reportError(new Error('manual boom'), 'login')

    expect(reporter).toHaveBeenCalledTimes(1)
    expect(reporter.mock.calls[0][0]).toMatchObject({
      message: 'manual boom',
      source: 'manual',
      context: 'login',
    })
  })

  it('reportError 也接受非 Error（String 化）', () => {
    const reporter = vi.fn()
    setErrorReporter(reporter)

    reportError('plain string', 'ws')

    expect(reporter.mock.calls[0][0].message).toBe('plain string')
    expect(reporter.mock.calls[0][0].context).toBe('ws')
  })
})
