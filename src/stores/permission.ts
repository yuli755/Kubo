import { PermissionUtil } from '@airpower/web'
import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * # 权限状态 Store
 *
 * 封装 @airpower/web 的 PermissionUtil，提供响应式权限列表。
 * 持久化到 sessionStorage，会话级别保留。
 *
 * 后续可逐步替换 PermissionUtil 的调用点。
 */
export const usePermissionStore = defineStore(
  'permission',
  () => {
    /**
     * ### 权限标识列表
     */
    const permissions = ref<string[]>([])

    /**
     * ### 是否已加载
     */
    const loaded = ref(false)

    /**
     * ### 设置权限列表
     * 同时写入 PermissionUtil 供 airpower 框架组件使用
     */
    function setPermissions(list: string[]) {
      permissions.value = list
      loaded.value = true
      PermissionUtil.saveList(list)
    }

    /**
     * ### 检查是否有指定权限
     */
    function has(permission: string): boolean {
      return permissions.value.includes(permission)
    }

    /**
     * ### 检查是否有任一权限（多权限 OR）
     */
    function hasAny(...perms: string[]): boolean {
      return perms.some(p => permissions.value.includes(p))
    }

    /**
     * ### 检查是否有全部权限（多权限 AND）
     */
    function hasAll(...perms: string[]): boolean {
      return perms.every(p => permissions.value.includes(p))
    }

    /**
     * ### 清除权限
     */
    function clear() {
      permissions.value = []
      loaded.value = false
    }

    return {
      permissions,
      loaded,
      setPermissions,
      has,
      hasAny,
      hasAll,
      clear,
    }
  },
  {
    persist: {
      key: 'kubo:permission',
      storage: 'session',
      pick: ['permissions', 'loaded'],
    },
  },
)
