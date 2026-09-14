// @vitest-environment jsdom
import type { AxiosResponse } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * `axios` 请求层契约测试（安全/健壮性主线的「锁」）。
 *
 * 锁住请求/响应两条拦截器的关键不变量：
 * - 请求侧：Authorization（Bearer token）、X-Tenant-Id（租户意图，tenantAware=false 时跳过）、
 *   Idempotency-Key（仅写方法）都在发出去前正确注入；无 token 时不伪造 Authorization；
 * - 响应侧：统一响应格式 {code:200,data} 解包为 data；业务错误码（非 200/0）转 reject；
 *   blob/arraybuffer 下载响应原样透传（不被 JSON 解包破坏）。
 *
 * 401 刷新重试 / 跳转登录在「阶段 E-M4 修复」后由本文件下方 describe 直接锁定
 * （刷新单飞、重试仍 401 的登出兜底、认证接口豁免），stores/user.spec.ts 覆盖 store 侧。
 */

// 可控的 user store 替身：拦截器在「请求时」取 useUserStore()，改这里即可切换登录态/租户
const userStore = {
  token: '',
  tenantId: undefined as number | undefined,
  refreshToken: vi.fn(async () => {}),
  logout: vi.fn(async () => {}),
}
vi.mock('@/stores/user', () => ({
  useUserStore: () => userStore,
}))

const elMessage = {
  error: vi.fn(),
  success: vi.fn(),
  warning: vi.fn(),
}
vi.mock('element-plus', () => ({
  ElMessage: elMessage,
}))

const { request } = await import('@/api/axios')

/** 用自定义 adapter 回放固定响应（不真发网络） */
function adapterReturning(payload: unknown, status = 200): (config: any) => Promise<AxiosResponse> {
  return async (config: any) => ({
    data: payload,
    status,
    statusText: status === 200 ? 'OK' : 'Error',
    headers: {},
    config,
  })
}

/** 用自定义 adapter 捕获「拦截器处理后的最终 config」，再回放空响应 */
function captureAdapter(capture: (config: any) => void): (config: any) => Promise<AxiosResponse> {
  return async (config: any) => {
    capture(config)
    return {
      data: {},
      status: 200,
      statusText: 'OK',
      headers: {},
      config,
    }
  }
}

beforeEach(() => {
  userStore.token = ''
  userStore.tenantId = undefined
  vi.clearAllMocks()
})

describe('axios 请求层 · 请求拦截器', () => {
  it('有 token 时注入 Authorization: Bearer', async () => {
    userStore.token = 't-abc'
    let captured: any
    await request.get('/x', {
      adapter: captureAdapter((c) => {
        captured = c
      }),
    })

    expect(captured.headers.Authorization).toBe('Bearer t-abc')
  })

  it('无 token 时不伪造 Authorization 头', async () => {
    let captured: any
    await request.get('/x', {
      adapter: captureAdapter((c) => {
        captured = c
      }),
    })

    expect(captured.headers.Authorization).toBeUndefined()
  })

  it('有租户意图时注入 X-Tenant-Id；tenantAware=false 时跳过', async () => {
    userStore.tenantId = 42

    let withTenant: any
    await request.get('/a', {
      adapter: captureAdapter((c) => {
        withTenant = c
      }),
    })
    expect(withTenant.headers['X-Tenant-Id']).toBe('42')

    let skipTenant: any
    await request.get('/b', {
      tenantAware: false,
      adapter: captureAdapter((c) => {
        skipTenant = c
      }),
    } as any)
    expect(skipTenant.headers['X-Tenant-Id']).toBeUndefined()
  })

  it('写方法（POST/PUT/PATCH）注入 Idempotency-Key，GET 不注入', async () => {
    let postCfg: any
    await request.post('/p', {}, {
      adapter: captureAdapter((c) => {
        postCfg = c
      }),
    })
    expect(typeof postCfg.headers['Idempotency-Key']).toBe('string')
    expect(postCfg.headers['Idempotency-Key']).not.toBe('')

    let getCfg: any
    await request.get('/g', {
      adapter: captureAdapter((c) => {
        getCfg = c
      }),
    })
    expect(getCfg.headers['Idempotency-Key']).toBeUndefined()
  })
})

describe('axios 请求层 · 响应拦截器', () => {
  it('统一格式 {code:200,data} 解包为 data', async () => {
    const data = await request.get('/ok', { adapter: adapterReturning({ code: 200, message: 'ok', data: { id: 1 }, timestamp: '' }) })
    expect(data).toEqual({ id: 1 })
  })

  it('code=0 也视为成功', async () => {
    const data = await request.get('/ok0', { adapter: adapterReturning({ code: 0, message: 'ok', data: 'zero' }) })
    expect(data).toBe('zero')
  })

  it('业务错误码转 reject（不 resolve）', async () => {
    await expect(
      request.get('/err', { adapter: adapterReturning({ code: 40001, message: '业务失败', data: null }) }),
    ).rejects.toMatchObject({ code: 40001, message: '业务失败' })
  })

  it('blob 下载响应原样透传（不被 JSON 解包破坏）', async () => {
    const blob = new Blob(['x'], { type: 'text/plain' })
    const resp = await request.get('/file', {
      responseType: 'blob',
      adapter: adapterReturning(blob),
    })
    expect(resp.data).toBe(blob)
  })
})

/**
 * 阶段 E-M4：401 刷新单飞与会话终结兜底。
 * 锁住三条不变量（均为原实现真实缺陷）：
 * - 并发 401 只触发一次 /auth 刷新（刷新通常伴随轮换，重复刷新会连环失效）；
 * - 刷新成功 → 重试原请求并成功返回；
 * - 刷新失败 / 重试仍 401 → 登出 + replace('/login')，不把用户留在「已登录但处处 401」；
 * - /user/login* 自身的 401（密码错误）绝不触发刷新流程。
 */
describe('axios 请求层 · 401 刷新与登出', () => {
  const locationReplace = vi.fn()

  beforeEach(() => {
    Object.defineProperty(window, 'location', {
      configurable: true,
      writable: true,
      value: { replace: locationReplace, href: 'http://localhost/' },
    })
    userStore.refreshToken = vi.fn(async () => {})
    userStore.logout = vi.fn(async () => {})
    locationReplace.mockClear()
  })

  /** 前 failTimes 次返回 401，之后成功返回 payload 的适配器 */
  function flaky401Adapter(failTimes: number, payload: unknown = { code: 200, message: 'ok', data: 'retried' }) {
    let calls = 0
    return async (config: any) => {
      calls += 1
      if (calls <= failTimes) {
        const err: any = new Error('Request failed with status code 401')
        err.isAxiosError = true
        err.config = config
        err.response = { status: 401, data: { message: 'unauthorized' }, headers: {}, config }
        throw err
      }
      return { data: payload, status: 200, statusText: 'OK', headers: {}, config }
    }
  }

  it('401 → 刷新成功 → 自动重试原请求', async () => {
    const data = await request.get('/need-token', { adapter: flaky401Adapter(1) })
    expect(data).toBe('retried')
    expect(userStore.refreshToken).toHaveBeenCalledTimes(1)
    expect(userStore.logout).not.toHaveBeenCalled()
    expect(locationReplace).not.toHaveBeenCalled()
  })

  it('并发 401 共享同一次刷新（single-flight）', async () => {
    let refreshCalls = 0
    let resolveRefresh: () => void = () => {}
    userStore.refreshToken = vi.fn(() => {
      refreshCalls += 1
      return new Promise<void>((resolve) => {
        resolveRefresh = resolve
      })
    })
    const a = request.get('/c1', { adapter: flaky401Adapter(1) })
    const b = request.get('/c2', { adapter: flaky401Adapter(1) })
    // 两个请求都已撞上 401 并汇入同一次刷新
    await new Promise(r => setTimeout(r, 10))
    expect(refreshCalls).toBe(1)
    resolveRefresh()
    expect(await a).toBe('retried')
    expect(await b).toBe('retried')
  })

  it('刷新失败 → 登出并 replace 到 /login，原请求 reject', async () => {
    userStore.refreshToken = vi.fn(async () => {
      throw new Error('refresh rejected')
    })
    await expect(request.get('/dead', { adapter: flaky401Adapter(1) })).rejects.toBeDefined()
    expect(userStore.logout).toHaveBeenCalledTimes(1)
    expect(locationReplace).toHaveBeenCalledWith('/login')
  })

  it('刷新成功但重试仍 401 → 判定会话终结，不再无限刷新', async () => {
    // failTimes=99：刷新后重试仍然 401（例如服务端已吊销会话）
    await expect(request.get('/revoked', { adapter: flaky401Adapter(99) })).rejects.toBeDefined()
    // 只刷新一次；第二次 401 走兜底登出
    expect(userStore.refreshToken).toHaveBeenCalledTimes(1)
    expect(userStore.logout).toHaveBeenCalledTimes(1)
    expect(locationReplace).toHaveBeenCalledWith('/login')
  })

  it('登录接口自身的 401（密码错误）不触发刷新/登出/跳转', async () => {
    await expect(
      request.post('/user/login', {}, { adapter: flaky401Adapter(99) }),
    ).rejects.toBeDefined()
    expect(userStore.refreshToken).not.toHaveBeenCalled()
    expect(userStore.logout).not.toHaveBeenCalled()
    expect(locationReplace).not.toHaveBeenCalled()
  })
})
