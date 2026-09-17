import { describe, expect, it, vi } from 'vitest'
import { configureTransport, getTransport, hasTransport } from '@/framework/transport'

/**
 * # 传输层接缝的契约（`src/framework/transport.ts`）
 *
 * 这条接缝存在的意义：框架的 HTTP / 上传**不再** `import` 应用实例
 * （那会让 `framework` 依赖应用装配面，前端分层门禁 LF2 只能给它登记一条"例外"），
 * 改成应用把实例注册进来。接缝本身很小，但两个方向都必须钉住：
 *
 * 1. **装配是自动的**：`src/api/axios.ts` 在模块加载时注册自己 —— 组合根（`src/main.ts`）
 *    显式引入它之后，框架侧就能发请求。少了这一步，编译、lint、类型全都通过，
 *    只有运行时"点了没反应"。
 * 2. **没装配就抛错**：不能静默不发请求 —— 这正是当初不敢拆那条依赖的理由，
 *    现在把它变成一次显式的失败。
 */
describe('framework · 传输层接缝', () => {
  it('统一实例在模块加载时注册自己（框架侧于是不必认识 api/axios）', async () => {
    // 本文件里首个用例：此刻统一实例还没被加载过（vitest 每个测试文件独立模块图），
    // 所以先把它注销掉 —— 下面那次 import 才是真的"从零装配"。
    // 坑：这里**不能**用 `vi.resetModules()` 换一份模块图 —— 那会连
    // `framework/transport.ts` 一起重置，注册写进新副本、断言读的是旧副本（表现为"没注册"）。
    configureTransport(null)
    expect(hasTransport(), '先确认是真拆了，下面那次装配才有意义').toBe(false)

    await import('@/api/axios')

    expect(hasTransport(), 'src/api/axios.ts 没有注册传输层：框架侧第一个请求就会抛错').toBe(true)
    expect(getTransport().request).toBeTypeOf('function')
    expect(getTransport().upload).toBeTypeOf('function')
  })

  it('未装配时抛错，而不是静默不发请求', () => {
    const real = getTransport()
    try {
      configureTransport(null)
      expect(() => getTransport()).toThrow(/未装配/)
    }
    finally {
      configureTransport(real)
    }
  })

  it('取用方拿到的是装配进去的那个实现', async () => {
    const real = getTransport()
    const request = vi.fn().mockResolvedValue({ ok: true })
    try {
      configureTransport({ request, upload: vi.fn() })
      await expect(getTransport().request({ url: '/demo/getPage' })).resolves.toEqual({ ok: true })
      expect(request).toHaveBeenCalledWith({ url: '/demo/getPage' })
    }
    finally {
      configureTransport(real)
    }
  })
})
