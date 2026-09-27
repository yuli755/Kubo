// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { inlineTruncate, redactSecrets, reportErrorToServer } from '@/utils/errorReportClient'

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

  it('脱敏 S-G2：发出的 body 不含明文凭证（query/form/JSON/Bearer 四种形态）', () => {
    const { fetchSpy } = mockFetchOnce()

    reportErrorToServer({
      message: 'request failed https://x/api?accessToken=eyJhbGci.abc.def',
      stack: 'at f (config {"password":"P@ssw0rd!","user":"u"})',
      source: 'manual',
      context: 'ws Bearer eyJhbGciOiJSUzIv-_+/=',
      time: 1,
    })

    const body = String(fetchSpy.mock.calls[0][1].body)
    expect(body).not.toContain('eyJhbGci.abc.def')
    expect(body).not.toContain('P@ssw0rd!')
    expect(body).not.toContain('eyJhbGciOiJSUzIv-_+/=')
    // 键名保留，便于排查「这里曾有凭证」
    expect(body).toContain('accessToken')
    expect(body).toContain('password')
    // 正常字段不受影响
    expect(body).toContain('user')
  })
})

describe('redactSecrets · 文本脱敏规则', () => {
  it.each([
    ['?accessToken=abc.def', '?accessToken=***'],
    ['"refreshToken": "rt-123"', '"refreshToken": ***'],
    ['Authorization: Bearer eyJhbGciOi.ABC-_123', 'Authorization: ***'],
    ['header apiKey = AK-9527 end', 'header apiKey = *** end'],
  ])('替换 %s', (input, expected) => {
    expect(redactSecrets(input)).toBe(expected)
  })

  it('普通文本原样保留', () => {
    expect(redactSecrets('Cannot read properties of undefined (reading "id")')).toBe(
      'Cannot read properties of undefined (reading "id")',
    )
  })
})

describe('inlineTruncate · K-4 出网自净（压平换行 + 截断）', () => {
  it('压平全部 CRLF/LF 换行', () => {
    expect(inlineTruncate('a\r\nb\nc', 100)).toBe('a b c')
  })

  it('超长截断并带标记；未超长原样（压平后）返回', () => {
    const long = 'x'.repeat(300)
    const out = inlineTruncate(long, 200)
    // 截到 200 字符 + 「…(truncated)」标记（12 字符）
    expect(out).toHaveLength(212)
    expect(out).toContain('(truncated)')
    expect(inlineTruncate('short', 200)).toBe('short')
  })

  it('发出的 body 不含裸换行，message/stack 截到 2000、context 截到 200（与后端口径一致）', () => {
    const { fetchSpy } = mockFetchOnce()

    reportErrorToServer({
      message: `login failed\r\n[admin] fake log line ${'y'.repeat(3000)}`,
      stack: 'at a\r\nat b',
      source: 'manual',
      context: `ctx\r\ninjected ${'z'.repeat(500)}`,
      time: 1,
    })

    const body = String(fetchSpy.mock.calls[0][1].body)
    // body 是 JSON 字符串：裸的 \r\n 以 \\r\\n 转义存在才算安全，这里断言解码后的字段值
    const parsed = JSON.parse(body)
    expect(parsed.message).not.toMatch(/[\r\n]/)
    expect(parsed.stack).not.toMatch(/[\r\n]/)
    expect(parsed.context).not.toMatch(/[\r\n]/)
    expect(parsed.message.length).toBeLessThanOrEqual(2012)
    expect(parsed.message).toContain('(truncated)')
    expect(parsed.context.length).toBeLessThanOrEqual(212)
    // 注入的假日志行被压平后仍是普通文本，不构成独立行
    expect(parsed.message).toContain('[admin] fake log line')
  })
})
