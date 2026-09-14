// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { reportErrorToServer } from '@/utils/errorReportClient'

/**
 * `errorReportClient` 契约测试（生产可观测性闭环）。
 *
 * 锁住三条底线：
 * 1. 错误必须 POST 到后端 `/api/error-report`（闭环打通，不再只落 console）；
 * 2. 同一错误在去重窗口内只报一次（防「错误风暴」把后端限流/日志打爆）；
 * 3. 上报失败静默（不递归上报、不抛错、不阻塞业务）。
 */

function mockFetchOnce(): { fetchSpy: ReturnType<typeof vi.fn> } {
  const fetchSpy = vi.fn().mockResolvedValue(new Response(null, { status: 200 }))
  vi.stubGlobal('fetch', fetchSpy)
  return { fetchSpy }
}

describe('errorReportClient · 前端错误上报', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('错误 POST 到后端 /api/error-report，body 含结构化字段', () => {
    const { fetchSpy } = mockFetchOnce()

    reportErrorToServer({
      message: 'boom',
      stack: 'Error: boom',
      source: 'vue',
      context: 'device-monitor',
      time: 123,
    })

    expect(fetchSpy).toHaveBeenCalledTimes(1)
    const [url, options] = fetchSpy.mock.calls[0]
    expect(url).toBe('/api/error-report')
    expect(options.method).toBe('POST')
    expect(JSON.parse(options.body)).toMatchObject({
      message: 'boom',
      source: 'vue',
      context: 'device-monitor',
    })
  })

  it('同一 source:message 在去重窗口内只报一次', () => {
    const { fetchSpy } = mockFetchOnce()
    const report = { message: 'same error', stack: '', source: 'window' as const, time: 1 }

    reportErrorToServer(report)
    reportErrorToServer(report)

    expect(fetchSpy).toHaveBeenCalledTimes(1)
  })

  it('不同 context 但相同 message+source 仍视为同一条（去重看 source:message）', () => {
    const { fetchSpy } = mockFetchOnce()

    reportErrorToServer({ message: 'm', source: 'manual', context: 'a', time: 1 })
    reportErrorToServer({ message: 'm', source: 'manual', context: 'b', time: 1 })

    expect(fetchSpy).toHaveBeenCalledTimes(1)
  })

  it('不同 message 或不同 source 不会被折叠', () => {
    const { fetchSpy } = mockFetchOnce()

    reportErrorToServer({ message: 'a', source: 'vue', time: 1 })
    reportErrorToServer({ message: 'b', source: 'vue', time: 1 })
    reportErrorToServer({ message: 'a', source: 'promise', time: 1 })

    expect(fetchSpy).toHaveBeenCalledTimes(3)
  })

  it('上报失败静默：reject 不抛错、不递归', async () => {
    const fetchSpy = vi.fn().mockRejectedValue(new Error('network down'))
    vi.stubGlobal('fetch', fetchSpy)

    // 同步返回 void（内部 .catch 吞掉 reject），不得抛错
    expect(() =>
      reportErrorToServer({ message: 'x', source: 'manual', time: 1 }),
    ).not.toThrow()
    expect(fetchSpy).toHaveBeenCalledTimes(1)
  })
})
