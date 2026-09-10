// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { installRouterErrorHandler, isChunkLoadError, reloadToRecover } from '@/utils/routerErrorHandler'

/**
 * `routerErrorHandler` 契约测试。
 *
 * 锁住三条底线：
 * 1. `isChunkLoadError` 只识别「懒加载 chunk 失败」特征（发版时新旧 bundle 不匹配），
 *    普通业务异常不误判；
 * 2. `installRouterErrorHandler` 把导航错误以 context=`router` 上报；
 * 3. chunk 失败会触发刷新，且时间窗守卫防止同一会话内刷新风暴。
 */

const reportError = vi.hoisted(() => vi.fn())

vi.mock('@/utils/errorHandler', () => ({
  reportError,
}))

function makeRouter(): ReturnType<typeof createRouter> {
  return createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', component: {} }],
  })
}

/** 捕获 installRouterErrorHandler 注册的 onError 回调 */
function captureHandler(router: ReturnType<typeof createRouter>): (e: unknown) => void {
  let captured: ((e: unknown) => void) | null = null
  vi.spyOn(router, 'onError').mockImplementation((handler) => {
    captured = handler as unknown as (e: unknown) => void
    return () => {}
  })
  return (e: unknown) => {
    if (!captured) {
      throw new Error('onError 尚未注册')
    }
    captured(e)
  }
}

describe('routerErrorHandler · 导航/chunk 加载错误兜底', () => {
  beforeEach(() => {
    reportError.mockReset()
    sessionStorage.clear()
  })

  it('isChunkLoadError 识别各浏览器 chunk 失败特征文案', () => {
    expect(isChunkLoadError(new Error('Failed to fetch dynamically imported module: /assets/x.js'))).toBe(true)
    expect(isChunkLoadError(new Error('Loading chunk 3 failed.'))).toBe(true)
    expect(isChunkLoadError(new Error('Importing a module script failed.'))).toBe(true)
  })

  it('isChunkLoadError 不误判普通业务异常', () => {
    expect(isChunkLoadError(new Error('render boom'))).toBe(false)
    expect(isChunkLoadError('plain string error')).toBe(false)
  })

  it('installRouterErrorHandler 以 context=router 上报导航错误', () => {
    const router = makeRouter()
    const trigger = captureHandler(router)
    installRouterErrorHandler(router, vi.fn())

    // 用非 chunk 错误触发上报路径（避免同时触发 reload 的副作用）
    trigger(new Error('guard failed'))
    expect(reportError).toHaveBeenCalledTimes(1)
    const [err, context] = reportError.mock.calls[0]
    expect(err).toBeInstanceOf(Error)
    expect(context).toBe('router')
  })

  it('reloadToRecover 时间窗内只刷新一次', () => {
    const reload = vi.fn()
    reloadToRecover(reload)
    reloadToRecover(reload) // 时间窗内第二次调用，应被守卫拦下
    expect(reload).toHaveBeenCalledTimes(1)
  })

  it('chunk 失败经 installRouterErrorHandler 触发上报 + 刷新', () => {
    const router = makeRouter()
    const trigger = captureHandler(router)
    const reload = vi.fn()
    installRouterErrorHandler(router, reload)

    trigger(new Error('Loading chunk 1 failed.'))

    expect(reportError).toHaveBeenCalledTimes(1)
    expect(reload).toHaveBeenCalledTimes(1)
  })
})
