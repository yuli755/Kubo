import { PermissionUtil } from '@airpower/web'
import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

export const usePermissionStore = defineStore(
  'permission',
  () => {
    const permissions = ref<string[]>([])
    const loaded = ref(false)

    function setPermissions(list: string[]) {
      permissions.value = list
      loaded.value = true
      PermissionUtil.saveList(list)
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
      PermissionUtil.saveList([])
    }

    watch(permissions, (list) => {
      if (list && list.length > 0) {
        PermissionUtil.saveList(list)
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
