import { defineStore } from 'pinia'
import { ref } from 'vue'

export const usePermissionStore = defineStore(
  'permission',
  () => {
    const permissions = ref<string[]>([])
    const loaded = ref(false)

    // 第 35 轮审计：历史上这里还把权限列表镜像写进 localStorage['permissions']
    // （permissionCache），但生产代码没有任何读取方 —— 真实读者只有本 store
    // （persist 到 sessionStorage `kubo:permission`）。死镜像已删除；这里清一次
    // 老版本留下的残留键，清完即止（removeItem 对不存在的键是 no-op）。
    try {
      localStorage.removeItem('permissions')
    }
    catch {
      // 隐私模式下存储不可用：本来也没有残留可清
    }

    function setPermissions(list: string[]) {
      permissions.value = list
      loaded.value = true
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
    }

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
