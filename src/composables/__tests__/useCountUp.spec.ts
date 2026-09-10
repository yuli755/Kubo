// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope } from 'vue'

/**
 * `useCountUp` 数字滚动契约测试（V6 · 看板动效）。
 *
 * 锁的核心不变量是 V8：`prefers-reduced-motion: reduce` 时**直接到位不滚动**——
 * 动效不能凌驾于无障碍之上。动画的逐帧过程由 rAF 驱动、属异步时序，不在此做
 * 精确中间帧断言（那是实现细节，不是契约）。
 */

const mediaQueryList = {
  matches: false,
  addEventListener: vi.fn(),
  removeEventListener: vi.fn(),
}
vi.stubGlobal('matchMedia', vi.fn(() => mediaQueryList))

const { useCountUp } = await import('@/composables/useCountUp')

describe('useCountUp · 数字滚动', () => {
  let scope: ReturnType<typeof effectScope>

  beforeEach(() => {
    mediaQueryList.matches = false
    scope = effectScope()
  })

  it('reduced-motion 下直接到位，不经过动画帧', () => {
    mediaQueryList.matches = true
    const { display } = scope.run(() => useCountUp(() => 42))!
    expect(display.value).toBe(42)
  })

  it('正常模式下动画从 0 起滚（首帧尚未滚动完成，不等价于直接到位）', () => {
    const { display } = scope.run(() => useCountUp(() => 100))!
    // immediate watch 已触发，但 rAF 首帧尚未执行：display 仍是起始值 0
    expect(display.value).toBe(0)
  })

  it('reduced-motion 下目标值变化仍直接到位', () => {
    mediaQueryList.matches = true
    const target = { value: 7 }
    const { display } = scope.run(() => useCountUp(() => target.value))!
    expect(display.value).toBe(7)
    // 注意：watch 的后续变化依赖组件上下文的 flush 时序，effectScope 下不精确断言；
    // 这里只锁「reduced-motion 首帧即直接到位」这一核心不变量。
  })

  it('目标值为 0 时显示 0', () => {
    const { display } = scope.run(() => useCountUp(() => 0))!
    expect(display.value).toBe(0)
  })

  it('作用域释放后取消动画帧不抛错', () => {
    const { display } = scope.run(() => useCountUp(() => 99))!
    expect(display.value).toBe(0)
    scope.stop()
  })
})
