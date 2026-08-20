import { onUnmounted, ref } from 'vue'

interface PollingOptions {
  /** 连续失败次数上限，达到后停止轮询并标记离线（默认 5） */
  maxRetries?: number
  /** 失败时回调 */
  onFail?: (consecutiveFailures: number) => void
  /** 达到最大失败次数时回调 */
  onOffline?: () => void
  /** 恢复在线时回调 */
  onOnline?: () => void
}

/**
 * # 轮询 Composable
 *
 * 封装 setInterval 轮询逻辑，提供：
 * - 连续失败计数与自动停止（达到 maxRetries 后暂停轮询）
 * - 浏览器离线检测（navigator.onLine + online/offline 事件）
 * - 离线后自动恢复（浏览器恢复在线时重新开始轮询）
 * - 响应式状态（isOffline、consecutiveFailures）
 *
 * 组件卸载时自动清理定时器和事件监听。
 *
 * @example
 * ```ts
 * const { isOffline, consecutiveFailures } = usePolling(
 *   async () => { await fetchData() },
 *   1000,
 *   { maxRetries: 5 },
 * )
 * ```
 */
export function usePolling(
  fn: () => Promise<void>,
  interval: number,
  options: PollingOptions = {},
) {
  const { maxRetries = 5, onFail, onOffline, onOnline } = options

  /** 是否处于离线状态（连续失败达到上限或浏览器离线） */
  const isOffline = ref(false)
  /** 连续失败次数 */
  const consecutiveFailures = ref(0)

  let timer: ReturnType<typeof setInterval> | null = null
  /** 是否被手动暂停（如弹窗期间） */
  let manuallyPaused = false

  function clearTimer() {
    if (timer !== null) {
      clearInterval(timer)
      timer = null
    }
  }

  function start() {
    if (manuallyPaused || isOffline.value) {
      return
    }
    clearTimer()
    fn()
    timer = setInterval(() => {
      fn()
    }, interval)
  }

  function stop() {
    clearTimer()
  }

  /**
   * 手动暂停轮询（不触发离线状态）
   */
  function pause() {
    manuallyPaused = true
    stop()
  }

  /**
   * 手动恢复轮询
   */
  function resume() {
    manuallyPaused = false
    if (!isOffline.value) {
      start()
    }
  }

  /** 标记一次失败，由调用方在 catch 中调用 */
  function markFail() {
    consecutiveFailures.value++
    onFail?.(consecutiveFailures.value)
    if (consecutiveFailures.value >= maxRetries && !isOffline.value) {
      isOffline.value = true
      stop()
      onOffline?.()
    }
  }

  /** 标记一次成功，重置失败计数 */
  function markSuccess() {
    if (consecutiveFailures.value > 0) {
      consecutiveFailures.value = 0
    }
    if (isOffline.value) {
      isOffline.value = false
      onOnline?.()
      start()
    }
  }

  function handleBrowserOffline() {
    if (!isOffline.value) {
      isOffline.value = true
      stop()
      onOffline?.()
    }
  }

  function handleBrowserOnline() {
    if (isOffline.value && !manuallyPaused) {
      isOffline.value = false
      consecutiveFailures.value = 0
      onOnline?.()
      start()
    }
  }

  window.addEventListener('offline', handleBrowserOffline)
  window.addEventListener('online', handleBrowserOnline)

  // 初始检查
  if (!navigator.onLine) {
    isOffline.value = true
  }

  onUnmounted(() => {
    clearTimer()
    window.removeEventListener('offline', handleBrowserOffline)
    window.removeEventListener('online', handleBrowserOnline)
  })

  return {
    isOffline,
    consecutiveFailures,
    start,
    stop,
    pause,
    resume,
    markFail,
    markSuccess,
  }
}
