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
 * 401 刷新重试 / 跳转登录涉及 useUserStore.refreshToken 与 window.location，属登录态集成，
 * 由 `stores/user.spec.ts` 覆盖，这里不重复。
 */

// 可控的 user store 替身：拦截器在「请求时」取 useUserStore()，改这里即可切换登录态/租户
const userStore = {
  token: '',
  tenantId: undefined as number | undefined,
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
