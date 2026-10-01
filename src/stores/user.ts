import type { TenantOption } from '@/api/system/types'
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { authToken } from '@/utils/authStorage'
/**
 * # 用户状态 Store
 *
 * 登录态有**两处**落盘，职责不同，别合并：
 * - `authToken`（localStorage key `Authorization`，见 `@/utils/authStorage`）：
 *   请求拦截器/刷新页面时的**权威来源**，同步写、同步读；
 * - persist 插件（key `kubo:user`）：带上租户、刷新令牌等，异步水合。
 *
 * 供 `src/api/axios.ts` 请求拦截器使用。
 */
export const useUserStore = defineStore(
  'user',
  () => {
    /**
     * ### 访问令牌
     * 初始化时从 `authToken` 恢复（支持页面刷新后 token 不丢失）
     */
    const token = ref<string>(authToken.get() || '')

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
      authToken.save(val)
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
     * ### 只清本地会话（不请求后端）
     *
     * R-38：跨标签页登出同步的收口动作 —— 别的标签页已经调过 `/user/logout`
     * 并摘掉了 `Authorization`，本标签页**绝不能再替用户登出一次**（那会用
     * 已作废的凭证再打一发请求，纯属噪音）；它只需要把这份登出「演给自己看」：
     * 清空内存态、缓存、权限，让路由守卫和请求拦截器立刻回到未登录口径。
     * `logout()` 的后端段之后复用同一函数，保证两处收口的字段清单不漂移。
     */
    function clearLocalSession() {
      token.value = ''
      tenantId.value = undefined
      tenantList.value = []
      userInfo.value = null
      refreshTokenValue.value = ''
      authToken.remove()
      localStorage.removeItem('kubo:user')
      // 阶段 E-M5：身份切换后跨用户缓存必须清零。react-query 缓存 key 不带
      // 用户身份（['wms:input','list'] 等），脱敏策略缓存按"登出/切租户应调用"
      // 的文档约定在此收口；否则低权限账号在同一标签页复用上一账号的读缓存。
      void import('@/query/queryClient').then(({ queryClient }) => queryClient.clear())
      void import('@/composables/useMaskPolicy').then(({ clearMaskPolicyCache }) => clearMaskPolicyCache())
      // 清除权限 store（延迟导入避免循环依赖）
      import('@/stores/permission').then(({ usePermissionStore }) => {
        usePermissionStore().clear()
      })
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
      clearLocalSession()
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
      clearLocalSession,
    }
  },
  {
    persist: {
      key: 'kubo:user',
      storage: 'local',
      // 阶段 E-H3：userInfo（getMyInfo 的 @ExposeAll 明文 PII：邮箱/手机号/实名）
      // 不再落 localStorage —— Console 每次挂载都会重新拉取，home 页有兜底；
      // 持久化只会把 PII 留在 XSS/物理接触可读的地方，且随 C-1 场景穿越登出。
      pick: ['token', 'refreshTokenValue', 'tenantId', 'tenantList'],
      /**
       * 阶段 E-C1（保留的防线）：只要 `authToken` 里的 token 不在了 —— 被别的标签页登出、
       * 被外部脚本/开发者工具清掉、或从未登录 —— 而 `kubo:user` 快照里还留着
       * token/refreshToken，就判定会话已注销并**丢弃快照**。
       * 少了这一条，快照会在下一次加载时把 token 复活，共享电脑上的"注销"形同虚设。
       */
      onRestore: (state) => {
        if (!authToken.get() && ((state as { token?: string }).token || (state as { refreshTokenValue?: string }).refreshTokenValue)) {
          return null
        }
        return state
      },
    },
  },
)
