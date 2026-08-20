import type { Directive, DirectiveBinding } from 'vue'
import { usePermissionStore } from '@/stores/permission'

/**
 * # 权限指令 v-permission
 *
 * 控制元素显隐。有权限 → 保留元素；无权限 → 移除元素。
 *
 * ## 使用示例
 * ```html
 * <!-- 单权限 -->
 * <el-button v-permission="'wms:inventory:create'">新建</el-button>
 *
 * <!-- 多权限（满足其一即可） -->
 * <el-button v-permission="['wms:inventory:export', 'wms:inventory:admin']">导出</el-button>
 * ```
 */
export const vPermission: Directive<HTMLElement, string | string[] | undefined> = {
  mounted(el: HTMLElement, binding: DirectiveBinding<string | string[] | undefined>) {
    const permissionStore = usePermissionStore()

    // 指令未传值 → 不拦截
    if (binding.value === undefined || binding.value === null) {
      return
    }

    const required = Array.isArray(binding.value) ? binding.value : [binding.value]
    const hasPermission = required.some(p => permissionStore.checkPermission(p))

    if (!hasPermission) {
      // 记录原始显示信息，供更新时恢复判断
      el.dataset.vPermissionFallback = el.style.display
      el.style.display = 'none'
    }
  },
  updated(el: HTMLElement, binding: DirectiveBinding<string | string[] | undefined>) {
    const permissionStore = usePermissionStore()

    if (binding.value === undefined || binding.value === null) {
      return
    }

    const required = Array.isArray(binding.value) ? binding.value : [binding.value]
    const hasPermission = required.some(p => permissionStore.checkPermission(p))

    if (hasPermission) {
      el.style.display = el.dataset.vPermissionFallback || ''
    }
    else {
      el.dataset.vPermissionFallback = el.style.display
      el.style.display = 'none'
    }
  },
}
