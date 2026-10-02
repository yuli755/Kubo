// @vitest-environment jsdom
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useUserStore } from '@/stores/user'
import { authToken } from '@/utils/authStorage'
import { reportErrorToServer } from '@/utils/errorReportClient'

/**
 * `errorReportClient` 契约测试（生产可观测性闭环）。
 *
 * 锁住四条底线：
 * 1. 错误必须 POST 到后端 `/api/error-report`（闭环打通，不再只落 console）；
 * 2. 同一错误在去重窗口内只报一次（防「错误风暴」把后端限流/日志打爆）；
 * 3. 上报失败静默（不递归上报、不抛错、不阻塞业务）；
 * 4. 身份/追踪头尽力而为（R40）：有 store 带 store，没 store 退回 authToken，
 *    但**任何情况下都必须发出去**——孤儿上报也胜过沉默。
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

  describe('身份与追踪头（R40）', () => {
    it('有 store：Authorization/X-Tenant-Id 取自 store，每次上报新发 X-Trace-Id', () => {
      setActivePinia(createPinia())
      const user = useUserStore()
      user.token = 'store-token'
      user.setTenantId(7)
      const { fetchSpy } = mockFetchOnce()

      reportErrorToServer({ message: 'hdr-a', source: 'manual', time: 1 })
      reportErrorToServer({ message: 'hdr-b', source: 'manual', time: 1 })

      const first = fetchSpy.mock.calls[0][1].headers
      const second = fetchSpy.mock.calls[1][1].headers
      expect(first.Authorization).toBe('Bearer store-token')
      expect(first['X-Tenant-Id']).toBe('7')
      expect(first['X-Trace-Id']).toMatch(/^[0-9a-f-]{36}$/)
      // traceId 每次上报新生成（关联的是「这一次上报」，复用了反而误导后端日志）
      expect(second['X-Trace-Id']).not.toBe(first['X-Trace-Id'])
    })

    it('租户未选择时不带 X-Tenant-Id（宁缺毋错，别让后端串租户）', () => {
      setActivePinia(createPinia())
      useUserStore().token = 'tk-no-tenant'
      const { fetchSpy } = mockFetchOnce()

      reportErrorToServer({ message: 'hdr-c', source: 'manual', time: 1 })

      const { headers } = fetchSpy.mock.calls[0][1]
      expect(headers.Authorization).toBe('Bearer tk-no-tenant')
      expect('X-Tenant-Id' in headers).toBe(false)
    })

    it('pinia 未就绪（引导期/登录页崩溃）：退回 authToken，匿名也必须发出去', () => {
      // 精确复现「store 还没装好 app 就崩了」：活动 pinia 为空 → useUserStore() 抛错。
      // （注意不能用 vi.resetModules 模拟——它不重置 pinia 的模块级活动实例，
      // 上一用例的 store 会被沿用，测不到抛错分支。）
      setActivePinia(undefined)
      authToken.save('ls-token')
      const { fetchSpy } = mockFetchOnce()

      expect(() => reportErrorToServer({ message: 'hdr-d', source: 'window', time: 1 })).not.toThrow()

      const { headers } = fetchSpy.mock.calls[0][1]
      expect(headers.Authorization).toBe('Bearer ls-token')
      expect('X-Tenant-Id' in headers).toBe(false)
      expect(headers['X-Trace-Id']).toMatch(/^[0-9a-f-]{36}$/)
      setActivePinia(createPinia())
    })
  })
})
