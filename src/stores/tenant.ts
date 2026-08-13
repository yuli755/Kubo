/**
 * # 租户信息 Store
 *
 * 管理当前租户的配置和状态。
 * 持久化策略：sessionStorage（关闭标签页即清除）。
 *
 * @module stores/tenant
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface TenantInfo {
  id: number
  name: string
  code: string
  logo?: string
  domain?: string
  /** 租户套餐类型 */
  plan?: 'basic' | 'professional' | 'enterprise'
  /** 租户过期时间 */
  expiresAt?: string
}

export const useTenantStore = defineStore('tenant', () => {
  const tenantId = ref<number | undefined>(undefined)
  const tenantName = ref('')
  const tenantCode = ref('')
  const tenantLogo = ref('')
  const tenantPlan = ref<'basic' | 'professional' | 'enterprise'>('basic')

  function setTenantInfo(info: TenantInfo) {
    tenantId.value = info.id
    tenantName.value = info.name
    tenantCode.value = info.code
    tenantLogo.value = info.logo || ''
    tenantPlan.value = info.plan || 'basic'
  }

  function clearTenant() {
    tenantId.value = undefined
    tenantName.value = ''
    tenantCode.value = ''
    tenantLogo.value = ''
    tenantPlan.value = 'basic'
  }

  return {
    tenantId,
    tenantName,
    tenantCode,
    tenantLogo,
    tenantPlan,
    setTenantInfo,
    clearTenant,
  }
}, {
  persist: {
    key: 'kubo:tenant',
    storage: 'session',
    pick: ['tenantId', 'tenantName', 'tenantCode', 'tenantLogo', 'tenantPlan'],
  },
})
