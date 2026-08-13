import { WebConfig } from '@airpower/web'
import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * # 用户状态 Store
 *
 * 与 @airpower/web 的 WebConfig 保持双向同步：
 * - `setToken` 同时写入 `WebConfig.saveAccessToken()`（兼容 airpower 框架）
 * - 同时通过 persist 插件持久化到 localStorage（key: `kubo:user`）
 *
 * 供 `src/api/axios.ts` 请求拦截器使用。
 */
export const useUserStore = defineStore(
  'user',
  () => {
    /**
     * ### 访问令牌
     * 初始化时从 WebConfig 恢复（支持页面刷新后 token 不丢失）
     */
    const token = ref<string>(WebConfig.getAccessToken() || '')

    /**
     * ### 刷新令牌（用于 refreshToken 流程）
     */
    const refreshTokenValue = ref<string>('')

    /**
     * ### 租户 ID
     */
    const tenantId = ref<number | undefined>(undefined)

    /**
     * ### 当前用户信息
     */
    const userInfo = ref<{
      avatar?: string
      nickname?: string
      username?: string
      [key: string]: unknown
    } | null>(null)

    function setToken(val: string) {
      token.value = val
      WebConfig.saveAccessToken(val)
    }

    function setTenantId(val: number | undefined) {
      tenantId.value = val
    }

    function setUserInfo(val: unknown) {
      userInfo.value = val as { avatar?: string, nickname?: string, username?: string, [key: string]: unknown } | null
    }

    function setRefreshToken(val: string) {
      refreshTokenValue.value = val
    }

    /**
     * ### 是否已登录
     */
    function isLoggedIn() {
      return !!token.value
    }

    /**
     * ### 刷新 Token
     * 调用 authApi.refreshToken，成功则更新本地 token
     */
    async function refreshToken() {
      if (!refreshTokenValue.value) {
        throw new Error('No refresh token available')
      }
      const { authApi } = await import('@/api/system')
      const res = await authApi.refreshToken({ refreshToken: refreshTokenValue.value })
      setToken(res.token)
      setRefreshToken(res.refreshToken)
    }

    /**
     * ### 登出
     * 清除所有用户状态，同时清理 WebConfig 持久化
     * 同步清除权限 store，避免残留权限标识
     */
    function logout() {
      token.value = ''
      tenantId.value = undefined
      userInfo.value = null
      refreshTokenValue.value = ''
      WebConfig.removeAccessToken()
      localStorage.removeItem('kubo:user')
      // 断开 WebSocket 连接（延迟导入避免循环依赖）
      import('@/websocket/WebSocketManager').then(({ webSocketManager }) => {
        webSocketManager.disconnect()
      })
      // 清除权限 store（延迟导入避免循环依赖）
      import('@/stores/permission').then(({ usePermissionStore }) => {
        usePermissionStore().clear()
      })
    }

    return {
      token,
      refreshTokenValue,
      tenantId,
      userInfo,
      setToken,
      setTenantId,
      setUserInfo,
      setRefreshToken,
      isLoggedIn,
      refreshToken,
      logout,
    }
  },
  {
    persist: {
      key: 'kubo:user',
      storage: 'local',
      pick: ['token', 'refreshTokenValue', 'tenantId', 'userInfo'],
    },
  },
)
