/**
 * PWA Service Worker 注册
 *
 * 使用 vite-plugin-pwa 的虚拟模块注册 Service Worker
 * 提供离线缓存、预加载、后台同步等 PWA 能力
 *
 * @see https://vite-pwa-org.netlify.app/
 */
import { registerSW } from 'virtual:pwa-register'
import { reportError } from '@/utils/errorHandler'

/** Service Worker 注册结果 */
interface SWRegistrationResult {
  /** 是否需要刷新（新版本可用） */
  needRefresh: boolean
  /** 更新 Service Worker */
  updateSW: (reloadPage?: boolean) => Promise<void>
  /** 是否离线 */
  offlineReady: boolean
}

/**
 * 注册 Service Worker
 *
 * @param onNeedRefresh - 新版本可用时的回调
 * @param onOfflineReady - 离线就绪时的回调
 * @returns 注册结果
 */
export function registerPWA(
  onNeedRefresh?: () => void,
  onOfflineReady?: () => void,
): SWRegistrationResult {
  let needRefresh = false
  let offlineReady = false

  const updateSW = registerSW({
    onNeedRefresh() {
      needRefresh = true
      onNeedRefresh?.()
    },
    onOfflineReady() {
      offlineReady = true
      onOfflineReady?.()
    },
    onRegistered(swRegistration) {
      // 可选：定期检查更新（每 1 小时）
      setInterval(() => {
        swRegistration?.update()
      }, 60 * 60 * 1000)
    },
    onRegisterError(error) {
      reportError(error, 'pwa-register')
    },
  })

  return {
    get needRefresh() { return needRefresh },
    get offlineReady() { return offlineReady },
    async updateSW(reloadPage = true) {
      await updateSW(reloadPage)
    },
  }
}

export default registerPWA
