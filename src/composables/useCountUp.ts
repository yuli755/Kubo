import { onScopeDispose, ref, watch } from 'vue'

/**
 * # 数字滚动动效（V6 · 看板动效）
 *
 * 零依赖，`requestAnimationFrame` 缓动。用于把「目标数字」渲染成从上次显示值平滑滚到新值的动画，
 * 典型场景是安全态势看板的 8 张数字卡。
 *
 * ## 为什么不用第三方 CountUp 库
 * ECharts 已占约 2.77MB、体积预算（总体 900KB gzip）已用 >98%，再引一个动画库会顶破预算；
 * 数字滚动只需几十行 rAF，自研零依赖（方案铁律：不引入运行时动效库）。
 *
 * ## 触发时机（克制，避免喧宾夺主）
 * - 首次挂载：从 0 滚到初值（仅一次）；
 * - 目标值变化：从「上一次显示值」滚到新值（TanStack Query 每次 refetch 若有变化才滚，不重复）；
 * - `prefers-reduced-motion: reduce`：直接跳到终值，不滚动（V8）。
 */

export interface CountUpOptions {
  /** 动画时长（ms），默认 600 */
  duration?: number
  /** 缓动函数，入参 t∈[0,1]，默认 easeOutCubic */
  easing?: (t: number) => number
}

/** easeOutCubic：前快后慢，数字滚动观感最自然 */
function easeOutCubic(t: number): number {
  return 1 - (1 - t) ** 3
}

export function useCountUp(target: () => number, options: CountUpOptions = {}) {
  const { duration = 600, easing = easeOutCubic } = options
  const display = ref(0)
  let rafId = 0
  let from = 0

  function animateTo(to: number): void {
    // 非浏览器环境 / 用户偏好减少动效：直接到位，不滚动
    if (typeof window === 'undefined' || window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      display.value = to
      from = to
      return
    }
    cancelAnimationFrame(rafId)
    const startValue = from
    const delta = to - startValue
    if (delta === 0) {
      display.value = to
      return
    }
    const start = performance.now()
    const step = (now: number): void => {
      const t = Math.min(1, (now - start) / duration)
      const eased = easing(t)
      display.value = Math.round(startValue + delta * eased)
      if (t < 1) {
        rafId = requestAnimationFrame(step)
      }
      else {
        display.value = to
        from = to
      }
    }
    rafId = requestAnimationFrame(step)
  }

  watch(target, val => animateTo(val), { immediate: true })

  onScopeDispose(() => {
    cancelAnimationFrame(rafId)
  })

  return { display }
}
