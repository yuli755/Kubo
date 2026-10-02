// @vitest-environment jsdom
import type { AxiosResponse } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { NETWORK_ERROR_CODE, resetNotifyDedupe } from '@/api/errorPolicy'
import { clearFieldError, fieldError } from '@/stores/fieldError'

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

  it('调用方自带 Idempotency-Key 时原样保留（第 35 轮审计）', async () => {
    // 幂等的语义是「同一逻辑操作同一把键」。拦截器无条件覆盖会把调用方
    // （外部 API 契约场景）精心管理的键静默作废 —— 承诺了幂等、实际没幂等。
    let cfg: any
    await request.post('/keep', {}, {
      headers: { 'Idempotency-Key': 'caller-key-1' },
      adapter: captureAdapter((c) => {
        cfg = c
      }),
    } as any)
    expect(cfg.headers['Idempotency-Key']).toBe('caller-key-1')
  })

  it('401 刷新后的重试复用同一把 Idempotency-Key（重试不是新操作）', async () => {
    // 401 重试是把 originalRequest 重新塞回请求拦截器 —— 若无条件重新生成，
    // 同一逻辑写操作就带两把键，服务端去重形同虚设（请求可能落库两次）。
    const seen: string[] = []
    let calls = 0
    const adapter = async (config: any) => {
      seen.push(String(config.headers['Idempotency-Key']))
      calls += 1
      if (calls === 1) {
        const err: any = new Error('401')
        err.isAxiosError = true
        err.config = config
        err.response = { status: 401, data: { message: 'unauthorized' }, headers: {}, config }
        throw err
      }
      return { data: { code: 200, message: 'ok', data: 'done' }, status: 200, statusText: 'OK', headers: {}, config }
    }
    const data = await request.post('/write', {}, { adapter })
    expect(data).toBe('done')
    expect(seen).toHaveLength(2)
    expect(seen[0]).toBeTruthy()
    expect(seen[1]).toBe(seen[0])
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
      // R-35 起 redirect 收口要读 pathname+search 拼来源，替身必须给全
      value: { replace: locationReplace, href: 'http://localhost/', pathname: '/', search: '' },
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

  it('刷新失败 → 登出并 replace 到 /login（带来源 redirect），原请求 reject', async () => {
    userStore.refreshToken = vi.fn(async () => {
      throw new Error('refresh rejected')
    })
    await expect(request.get('/dead', { adapter: flaky401Adapter(1) })).rejects.toBeDefined()
    expect(userStore.logout).toHaveBeenCalledTimes(1)
    // R-35：回登录页要带来源，重新登录后可回到原页面
    expect(locationReplace).toHaveBeenCalledWith('/login?redirect=%2F')
  })

  it('刷新成功但重试仍 401 → 判定会话终结，不再无限刷新', async () => {
    // failTimes=99：刷新后重试仍然 401（例如服务端已吊销会话）
    await expect(request.get('/revoked', { adapter: flaky401Adapter(99) })).rejects.toBeDefined()
    // 只刷新一次；第二次 401 走兜底登出
    expect(userStore.refreshToken).toHaveBeenCalledTimes(1)
    expect(userStore.logout).toHaveBeenCalledTimes(1)
    expect(locationReplace).toHaveBeenCalledWith('/login?redirect=%2F')
  })

  it('登录接口自身的 401（密码错误）不触发刷新/登出/跳转', async () => {
    await expect(
      request.post('/user/login', {}, { adapter: flaky401Adapter(99) }),
    ).rejects.toBeDefined()
    expect(userStore.refreshToken).not.toHaveBeenCalled()
    expect(userStore.logout).not.toHaveBeenCalled()
    expect(locationReplace).not.toHaveBeenCalled()
  })

  /**
   * R-35（真机复现 2026-10-01）：后端三个 advice 全部 @ResponseStatus(HttpStatus.OK)，
   * 鉴权失败（token 过期/被吊销）是 **HTTP 200 + 业务码 401** —— 按 HTTP status 分流的
   * 刷新/登出流程对它完全失效。此前只 toast 不跳转：stale token 冷启动的用户永远停在
   * Console 的「控制台初始化失败」错卡上，怎么重试都回不到登录页。
   */
  it('业务码 401（HTTP 200）→ 登出并带来源回登录页，不给人留在假会话里', async () => {
    await expect(
      request.post('/user/getMyTenantList', {}, {
        adapter: adapterReturning({ code: 401, message: '获取你的身份信息失败，请重新登录后再试' }),
      }),
    ).rejects.toMatchObject({ code: 401 })
    // 收口是 fire-and-forget（拦截器要先 reject 原请求），等一个宏任务让 finally 落地
    await new Promise(r => setTimeout(r, 0))
    expect(userStore.logout).toHaveBeenCalledTimes(1)
    expect(locationReplace).toHaveBeenCalledWith('/login?redirect=%2F')
  })

  it('登录接口自身的业务码 401 是业务结果，不触发登出/跳转（与 HTTP 401 同一豁免）', async () => {
    await expect(
      request.post('/user/login', {}, { adapter: adapterReturning({ code: 401, message: '身份信息获取失败' }) }),
    ).rejects.toMatchObject({ code: 401 })
    await new Promise(r => setTimeout(r, 0))
    expect(userStore.logout).not.toHaveBeenCalled()
    expect(locationReplace).not.toHaveBeenCalled()
  })

  it('并发业务码 401 只登出跳转一次（错卡并发失败不该连环 replace）', async () => {
    const adapter401 = () => adapterReturning({ code: 401, message: '登录状态已失效，请重新登录' })
    // 必须同时发起：串行 await 的话首个收口的 finally 已复位标志，第二个会再收口一次
    await Promise.allSettled([
      request.get('/a', { adapter: adapter401() }),
      request.get('/b', { adapter: adapter401() }),
    ])
    await new Promise(r => setTimeout(r, 0))
    expect(userStore.logout).toHaveBeenCalledTimes(1)
    expect(locationReplace).toHaveBeenCalledTimes(1)
  })
})

/**
 * 业务错误的用户提示（P1-2 / 风险表 R9）。
 *
 * 后端三个 advice 全部 `@ResponseStatus(HttpStatus.OK)`，业务失败一律是 **HTTP 200 + 业务码**，
 * 所以按 HTTP status 分流的 `case 403` 对它们是死分支；真正承接的是「code≠200」那一支 ——
 * 而那一支此前**只 reject 不提示**：用户点「保存」后界面毫无反应。
 * 这组用例锁住新契约：要说、要说得可操作、该静默时静默、并发失败不刷屏、字段名要传给表单。
 */
describe('axios 请求层 · 业务错误的用户提示', () => {
  /** 造一个直出 HTTP 错误的适配器（模拟网关 / Spring Security，而非后端业务码） */
  function httpErrorAdapter(status: number, data: unknown): (config: any) => Promise<AxiosResponse> {
    return async (config: any) => {
      const err: any = new Error(`Request failed with status code ${status}`)
      err.isAxiosError = true
      err.config = config
      err.response = { status, data, headers: {}, config }
      throw err
    }
  }

  beforeEach(() => {
    resetNotifyDedupe()
    clearFieldError()
  })

  it('4002 不再静默：弹出「标题：后端原文（该检查哪一项）」，并把字段名交给表单', async () => {
    await expect(
      request.post('/user/update', {}, {
        adapter: adapterReturning({ code: 4002, message: 'email: 邮箱格式不正确' }),
      }),
    ).rejects.toMatchObject({ code: 4002 })

    expect(elMessage.error).toHaveBeenCalledTimes(1)
    expect(elMessage.error).toHaveBeenCalledWith(expect.objectContaining({
      message: '提交内容有误：email: 邮箱格式不正确（请检查「email」这一项）',
      showClose: true,
    }))
    // 字段定位信号已发布，KFormDialog 会把出错项滚到视野内
    expect(fieldError.value?.field).toBe('email')
  })

  it('403 说清「找谁开通」，并给更长的停留时间（指引要读完）', async () => {
    await expect(
      request.get('/permission/list', { adapter: adapterReturning({ code: 403, message: '权限不足' }) }),
    ).rejects.toBeDefined()

    const arg = elMessage.error.mock.calls[0][0]
    expect(arg.message).toContain('无权限：权限不足')
    expect(arg.message).toContain('管理员')
    expect(arg.duration).toBe(6000)
  })

  it('调用方要求静默（showError:false）时只 reject，不弹全局提示', async () => {
    await expect(
      request.get('/quiet', { showError: false, adapter: adapterReturning({ code: 500, message: '炸了' }) } as never),
    ).rejects.toBeDefined()
    expect(elMessage.error).not.toHaveBeenCalled()
  })

  it('调用方给了 customErrorHandler 就完全交给它（不出现双份提示）', async () => {
    const handler = vi.fn()
    await expect(
      request.get('/custom', { customErrorHandler: handler, adapter: adapterReturning({ code: 4002, message: 'x: 不对' }) } as never),
    ).rejects.toBeDefined()

    expect(handler).toHaveBeenCalledTimes(1)
    expect(handler.mock.calls[0][0]).toMatchObject({ code: 4002, message: 'x: 不对' })
    expect(elMessage.error).not.toHaveBeenCalled()
  })

  it('登录失败（200001）保持静默：登录页自己呈现，全局不掺和', async () => {
    await expect(
      request.post('/user/login', {}, { adapter: adapterReturning({ code: 200001, message: '账号或密码不正确' }) }),
    ).rejects.toBeDefined()
    expect(elMessage.error).not.toHaveBeenCalled()
  })

  it('同一接口同一码连续失败只弹一次（列表页并发失败不该刷屏）', async () => {
    const adapter = adapterReturning({ code: 403, message: '权限不足' })
    await expect(request.get('/spam', { adapter })).rejects.toBeDefined()
    await expect(request.get('/spam', { adapter })).rejects.toBeDefined()
    await expect(request.get('/spam', { adapter })).rejects.toBeDefined()

    expect(elMessage.error).toHaveBeenCalledTimes(1)
  })

  it('不同接口各自提示，不会互相吞掉', async () => {
    await expect(request.get('/a', { adapter: adapterReturning({ code: 403, message: '权限不足' }) })).rejects.toBeDefined()
    await expect(request.get('/b', { adapter: adapterReturning({ code: 403, message: '权限不足' }) })).rejects.toBeDefined()
    expect(elMessage.error).toHaveBeenCalledTimes(2)
  })

  it('网关直出的 403（Security/反代）与业务码 403 共用同一套文案', async () => {
    await expect(
      request.get('/gateway', { adapter: httpErrorAdapter(403, { message: 'Forbidden' }) }),
    ).rejects.toBeDefined()

    expect(elMessage.error.mock.calls[0][0].message).toContain('无权限：Forbidden')
    expect(elMessage.error.mock.calls[0][0].message).toContain('管理员')
  })

  it('状态码 422 走 4002 的文案并解析出字段名', async () => {
    await expect(
      request.post('/validate', {}, { adapter: httpErrorAdapter(422, { message: 'name: 长度超限' }) }),
    ).rejects.toBeDefined()

    expect(elMessage.error.mock.calls[0][0].message).toContain('提交内容有误：name: 长度超限')
    expect(fieldError.value?.field).toBe('name')
  })

  it('状态码 500 也走统一文案（带 traceId 指引），不再是一句干巴巴的「服务器内部错误」', async () => {
    await expect(
      request.get('/boom', { adapter: httpErrorAdapter(500, { message: 'NPE' }) }),
    ).rejects.toBeDefined()

    const text = elMessage.error.mock.calls[0][0].message
    expect(text).toContain('服务器处理失败：NPE')
    expect(text).toContain('traceId')
  })
})

/**
 * 第 35 轮审计：网络层失败与残余状态码（404/429/未知）此前都是**裸 ElMessage**。
 * 裸提示意味着它们绕过了 showError:false、customErrorHandler 与 3s 去重 ——
 * 断网瞬间并发的 5 个请求就是 5 条红条，且后台静默刷新也照弹不误。
 * 这组用例锁「所有出 toast 的口子都收口在 notifyBusinessError」这条规则。
 */
describe('axios 请求层 · 网络失败与状态码统一收口', () => {
  function httpErrorAdapter(status: number, data: unknown): (config: any) => Promise<AxiosResponse> {
    return async (config: any) => {
      const err: any = new Error(`Request failed with status code ${status}`)
      err.isAxiosError = true
      err.config = config
      err.response = { status, data, headers: {}, config }
      throw err
    }
  }

  /** 无 response 的适配器（断网/超时的形状）；code 决定是超时还是断线 */
  function networkAdapter(code?: string): (config: any) => Promise<AxiosResponse> {
    return async (config: any) => {
      const err: any = new Error('network down')
      err.isAxiosError = true
      err.config = config
      if (code) {
        err.code = code
      }
      throw err
    }
  }

  beforeEach(() => {
    resetNotifyDedupe()
  })

  it('超时 → 分类文案「请求超时」带指引，reject 带合成码 -1', async () => {
    const err: any = await request
      .get('/slow', { adapter: networkAdapter('ECONNABORTED') })
      .then(() => null, (e: unknown) => e)
    expect(err.code).toBe(NETWORK_ERROR_CODE.timeout)
    expect(err.message).toBe('请求超时')
    const text = elMessage.error.mock.calls[0][0].message
    expect(text).toContain('请求超时（')
    expect(text).toContain('告知管理员')
  })

  it('断线（无 response 无 code）→ 「网络连接失败」带指引，reject 带合成码 -2', async () => {
    const err: any = await request
      .get('/gone', { adapter: networkAdapter() })
      .then(() => null, (e: unknown) => e)
    expect(err.code).toBe(NETWORK_ERROR_CODE.offline)
    expect(elMessage.error.mock.calls[0][0].message).toContain('网络连接失败（')
  })

  it('网络失败同样尊重 showError:false（后台批量刷新不该被红条打断）', async () => {
    await expect(
      request.get('/bg', { showError: false, adapter: networkAdapter() } as never),
    ).rejects.toBeDefined()
    expect(elMessage.error).not.toHaveBeenCalled()
  })

  it('断网时不同路径也只弹一条（全局同一件事，按路径去重压不住）', async () => {
    await expect(request.get('/a', { adapter: networkAdapter() })).rejects.toBeDefined()
    await expect(request.get('/b', { adapter: networkAdapter() })).rejects.toBeDefined()
    await expect(request.get('/c', { adapter: networkAdapter() })).rejects.toBeDefined()
    expect(elMessage.error).toHaveBeenCalledTimes(1)
  })

  it('主动取消（ERR_CANCELED）不是失败：不弹提示，原样 reject', async () => {
    await expect(
      request.get('/abort', { adapter: networkAdapter('ERR_CANCELED') }),
    ).rejects.toBeDefined()
    expect(elMessage.error).not.toHaveBeenCalled()
  })

  it('404 → 「数据不存在」统一文案；后端没给 message 时用兜底句', async () => {
    await expect(
      request.get('/missing', { adapter: httpErrorAdapter(404, { message: 'no such record' }) }),
    ).rejects.toBeDefined()
    const text = elMessage.error.mock.calls[0][0].message
    expect(text).toContain('数据不存在：no such record')
    expect(text).toContain('刷新')

    elMessage.error.mockClear()
    resetNotifyDedupe()
    await expect(
      request.get('/missing2', { adapter: httpErrorAdapter(404, undefined) }),
    ).rejects.toBeDefined()
    expect(elMessage.error.mock.calls[0][0].message).toContain('数据不存在：请求的资源不存在')
  })

  it('429 → 「请求过于频繁」，并且给"稍等几秒"的可执行指引', async () => {
    await expect(
      request.get('/spam', { adapter: httpErrorAdapter(429, undefined) }),
    ).rejects.toBeDefined()
    const text = elMessage.error.mock.calls[0][0].message
    expect(text).toContain('请求过于频繁')
    expect(text).toContain('稍等几秒')
  })

  it('未知状态码 → 有原文时原文降级为 detail，没有时状态码兜底', async () => {
    await expect(
      request.get('/teapot', { adapter: httpErrorAdapter(418, { message: 'teapot raw text' }) }),
    ).rejects.toBeDefined()
    expect(elMessage.error.mock.calls[0][0].message).toContain('请求失败：teapot raw text')

    elMessage.error.mockClear()
    resetNotifyDedupe()
    await expect(
      request.get('/teapot2', { adapter: httpErrorAdapter(418, undefined) }),
    ).rejects.toBeDefined()
    expect(elMessage.error.mock.calls[0][0].message).toContain('请求失败 (418)')
  })
})
