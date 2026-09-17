import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import { permissionCache } from '@/utils/authStorage'

export const usePermissionStore = defineStore(
  'permission',
  () => {
    const permissions = ref<string[]>([])
    const loaded = ref(false)

    function setPermissions(list: string[]) {
      permissions.value = list
      loaded.value = true
      permissionCache.saveList(list)
    }

    /** 核心权限检查方法 */
    function checkPermission(permission: string): boolean {
      return permissions.value.includes(permission)
    }

    function hasAny(...perms: string[]): boolean {
      return perms.some(p => permissions.value.includes(p))
    }

    function clear() {
      permissions.value = []
      loaded.value = false
      permissionCache.saveList([])
    }

    watch(permissions, (list) => {
      if (list && list.length > 0) {
        permissionCache.saveList(list)
      }
    }, { immediate: true })

    return {
      permissions,
      loaded,
      setPermissions,
      checkPermission,
      hasAny,
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
