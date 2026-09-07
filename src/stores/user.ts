import type { TenantOption } from '@kubo/shared'
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
     * ### 当前用户可切换租户
     *
     * 只接受后端返回的可选租户列表；前端不会自行拼接租户 ID。
     */
    const tenantList = ref<TenantOption[]>([])

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

    function setTenantList(val: TenantOption[]) {
      tenantList.value = val.filter(item => Number.isSafeInteger(item.id) && item.id > 0)
      const selected = tenantList.value.find(item => item.id === tenantId.value)
      const preferred = selected || tenantList.value.find(item => item.isDefault) || tenantList.value[0]
      tenantId.value = preferred?.id
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
     * 调用后端登出接口清除 Cookie，然后清除所有本地状态
     */
    async function logout() {
      // 调用后端登出接口（清除服务端 Cookie）
      try {
        const { authApi } = await import('@/api/system')
        await authApi.logout()
      }
      catch {
        // 静默忽略 — 即使后端调用失败也要清除本地状态
      }
      // 清除本地状态
      token.value = ''
      tenantId.value = undefined
      tenantList.value = []
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
      tenantList,
      userInfo,
      setToken,
      setTenantId,
      setTenantList,
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
      pick: ['token', 'refreshTokenValue', 'tenantId', 'tenantList', 'userInfo'],
    },
  },
)
