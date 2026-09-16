import type { TenantOption } from '@kubo/shared'
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { airpowerToken } from '@/utils/airpowerCompat'
/**
 * # 用户状态 Store
 *
 * 与 @airpower/web 的 WebConfig 保持双向同步（key `Authorization`）：
 * - `setToken` 同时写入 airpower 同 key 的 localStorage（兼容业务页 airpower 组件）
 * - 同时通过 persist 插件持久化到 localStorage（key: `kubo:user`）
 *
 * 供 `src/api/axios.ts` 请求拦截器使用。
 */
export const useUserStore = defineStore(
  'user',
  () => {
    /**
     * ### 访问令牌
     * 初始化时从 airpower 同 key 的 localStorage 恢复（支持页面刷新后 token 不丢失）
     */
    const token = ref<string>(airpowerToken.get() || '')

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
      airpowerToken.save(val)
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
      airpowerToken.remove()
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
      // 阶段 E-H3：userInfo（getMyInfo 的 @ExposeAll 明文 PII：邮箱/手机号/实名）
      // 不再落 localStorage —— Console 每次挂载都会重新拉取，home 页有兜底；
      // 持久化只会把 PII 留在 XSS/物理接触可读的地方，且随 C-1 场景穿越登出。
      pick: ['token', 'refreshTokenValue', 'tenantId', 'tenantList'],
      /**
       * 阶段 E-C1：@airpower/web 内置退出按钮只删 `Authorization` 键 + 跳登录页，
       * 既不调 /user/logout 也不清 kubo:user。若放任恢复，下一个页面加载会从
       * 快照复活 token/refreshToken —— 共享电脑上的"注销"形同虚设。
       * 规则：Authorization 键已不存在（被内置登出删除，或从未登录）而快照里
       * 还有 token → 判定会话已注销，丢弃快照。
       */
      onRestore: (state) => {
        if (!airpowerToken.get() && ((state as { token?: string }).token || (state as { refreshTokenValue?: string }).refreshTokenValue)) {
          return null
        }
        return state
      },
    },
  },
)
