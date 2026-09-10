import { describe, expect, it, vi } from 'vitest'
import { useRoutePreload } from '@/composables/useRoutePreload'

/**
 * 路由级预加载的契约测试：锁「同一工厂只触发一次」与「失败静默」。
 *
 * 预加载只负责提前触发 chunk 下载，不负责导航；因此它必须：
 * 1. 对同一工厂去重（重复触发只是浪费带宽，且 Vite 动态 import 本身会缓存，
 *    但这里在触发前就拦下，避免多余请求）；
 * 2. 失败不抛异常、不阻塞主流程（预加载是优化，绝不能变成故障源）。
 */

describe('useRoutePreload · 触发与去重契约', () => {
  it('单个 loader 触发一次', () => {
    const loader = vi.fn().mockResolvedValue('chunk')
    const { preload } = useRoutePreload()
    preload(loader)

    expect(loader).toHaveBeenCalledTimes(1)
  })

  it('同一 loader 重复预加载被去重，只触发一次', () => {
    const loader = vi.fn().mockResolvedValue('chunk')
    const { preload } = useRoutePreload()
    preload(loader)
    preload(loader)
    preload(loader)

    expect(loader).toHaveBeenCalledTimes(1)
  })

  it('数组批量预加载，每个 loader 各触发一次', () => {
    const a = vi.fn().mockResolvedValue('a')
    const b = vi.fn().mockResolvedValue('b')
    const { preload } = useRoutePreload()
    preload([a, b])

    expect(a).toHaveBeenCalledTimes(1)
    expect(b).toHaveBeenCalledTimes(1)
  })

  it('loader 抛错被静默吞掉，不向上抛出', () => {
    const loader = vi.fn().mockRejectedValue(new Error('network'))
    const { preload } = useRoutePreload()

    expect(() => preload(loader)).not.toThrow()
  })
})
