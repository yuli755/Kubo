// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * # `Http` 的选项必须真的传到请求层
 *
 * 曾经的缺陷：`hideError()` / `throwError()` 只把 `this.option.showError` 改成 false，
 * 而 `send()` 组装 axios 配置时**没有带上这个字段** —— 两个方法是彻底的空操作。
 * 调用方以为自己"自己处理错误"，实际仍会收到一条全局提示（双份提示），
 * 且因为方法名与注释都写着"不弹错误提示"，这个失效在代码评审里极难被发现。
 *
 * 这里锁住的是**选项的传递**（而不是提示本身，那部分在 `api/__tests__/axios.spec.ts`）：
 * 拦截器唯一能读到调用方意图的通道就是 axios config，所以必须逐项断言 config 里的值。
 */

const requestMock = vi.fn(async (_config: Record<string, unknown>) => ({}))
vi.mock('@/api/axios', () => ({
  request: requestMock,
  api: { get: requestMock, post: requestMock },
}))

const { Http } = await import('@/framework/http')

/** 取本次实际发出的 axios 配置 */
function sentConfig(): Record<string, unknown> {
  return requestMock.mock.calls.at(-1)?.[0] ?? {}
}

describe('框架 Http · showError 选项传递', () => {
  beforeEach(() => {
    requestMock.mockClear()
  })

  it('默认弹提示（showError: true）', async () => {
    await new Http('/default').request()
    expect(sentConfig().showError).toBe(true)
  })

  it('hideError() 后 showError 为 false，并且真的进了请求配置', async () => {
    await new Http('/quiet').hideError().request()
    expect(sentConfig().showError).toBe(false)
  })

  it('throwError() 同样静默（它表达的是「这个异常我自己处理」）', async () => {
    await new Http('/self-handled').throwError().request()
    expect(sentConfig().showError).toBe(false)
  })

  it('构造参数里的 showError 也会传下去（页面级批量静默）', async () => {
    await new Http('/ctor', { showError: false }).request()
    expect(sentConfig().showError).toBe(false)
  })

  it('url / method / timeout 与 showError 一起完整下发', async () => {
    await new Http('/full', { showError: false }).setTimeout(1234).request({ id: 1 })
    const config = sentConfig()
    expect(config.url).toBe('/full')
    expect(config.method).toBe('post')
    expect(config.timeout).toBe(1234)
    expect(config.showError).toBe(false)
  })
})
