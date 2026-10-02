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

  it('unhandledrejection 的 reason 会被上报为 promise 来源（监听器真的会跑，不只是注册了）', () => {
    const reporter = vi.fn()
    setErrorReporter(reporter)
    installGlobalErrorHandler(mockApp())

    const event = new Event('unhandledrejection') as Event & { reason?: unknown }
    event.reason = new Error('async boom')
    window.dispatchEvent(event)

    expect(reporter).toHaveBeenCalledWith(expect.objectContaining({
      message: 'async boom',
      source: 'promise',
    }))
  })

  it('window error：有 message 就用 message', () => {
    const reporter = vi.fn()
    setErrorReporter(reporter)
    installGlobalErrorHandler(mockApp())

    window.dispatchEvent(new ErrorEvent('error', { message: 'script boom' }))

    expect(reporter).toHaveBeenCalledWith(expect.objectContaining({
      message: 'script boom',
      source: 'window',
    }))
  })

  it('window error：资源加载失败没有 message，退化为 resource-load-error 而不是空串', () => {
    const reporter = vi.fn()
    setErrorReporter(reporter)
    installGlobalErrorHandler(mockApp())

    // <img>/<script> 的加载失败走这条：只有 target，没有 message。
    // 记成空串的话监控里会看到一堆「无内容错误」，等于没上报。
    const resourceEvent = new Event('error') as Event & { message?: string }
    resourceEvent.message = ''
    window.dispatchEvent(resourceEvent)

    expect(reporter).toHaveBeenCalled()
    expect(reporter.mock.calls[0][0].message).toMatch(/^resource-load-error/)
    expect(reporter.mock.calls[0][0].source).toBe('window')
  })

  it('弹窗取消的 cancel/close rejection 是用户意图：不上报，且 preventDefault 压掉控制台噪音', () => {
    // FeedbackUtil.confirm* 全家（26 处调用点）在取消时 reject('cancel')、Esc 时 reject('close')，
    // 调用点普遍不挂 catch。接了监控后，最大的噪音源会是「用户点了取消」——那是意图不是故障。
    const reporter = vi.fn()
    setErrorReporter(reporter)
    installGlobalErrorHandler(mockApp())

    const prevent = vi.spyOn(Event.prototype, 'preventDefault')
    for (const reason of ['cancel', 'close']) {
      const event = new Event('unhandledrejection', { cancelable: true }) as Event & { reason?: unknown }
      event.reason = reason
      window.dispatchEvent(event)
      expect(prevent).toHaveBeenCalled()
    }
    expect(reporter).not.toHaveBeenCalled()

    // 同一监听器仍然接真错误
    const real = new Event('unhandledrejection') as Event & { reason?: unknown }
    real.reason = new Error('still reported')
    window.dispatchEvent(real)
    expect(reporter).toHaveBeenCalledWith(expect.objectContaining({
      message: 'still reported',
      source: 'promise',
    }))
  })
})

/**
 * 默认 reporter（尚未接入 Sentry/自建监控时的兜底）单独一组，并且**放在文件最后**：
 * 它要靠 `vi.resetModules()` 拿到未被 `setErrorReporter` 改过的模块实例，
 * 而 resetModules 会影响其后的 import，顺序反过来会干扰前面那些用例。
 */
describe('errorHandler · 默认 reporter 落 console', () => {
  it('带来源标签，有 context 时追加 context，无 context 时不留空括号', async () => {
    vi.resetModules()
    const fresh = await import('@/utils/errorHandler')
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {})

    fresh.reportError(new Error('boom'), 'login')
    expect(spy).toHaveBeenCalledTimes(1)
    expect(spy.mock.calls[0][0]).toBe('[Kubo][global-manual]')
    expect(spy.mock.calls[0][1]).toBe('boom')
    expect(spy.mock.calls[0][3]).toBe(' (context=login)')

    spy.mockClear()
    fresh.reportError('no context here')
    expect(spy.mock.calls[0][0]).toBe('[Kubo][global-manual]')
    expect(spy.mock.calls[0][1]).toBe('no context here')
    expect(spy.mock.calls[0][3]).toBe('')

    vi.restoreAllMocks()
  })
})
