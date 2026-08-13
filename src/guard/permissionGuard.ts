import type { Router } from 'vue-router'
import { usePermissionStore } from '@/stores/permission'
import { useUserStore } from '@/stores/user'

/**
 * # 权限路由守卫
 *
 * 检查用户是否有所需权限，无权限则重定向到 /403。
 * 权限数据由登录流程通过 `permissionStore.setPermissions()` 写入，
 * 持久化到 sessionStorage，因此刷新页面后仍可用。
 *
 * ## 检查逻辑
 * 1. 未登录或访客路由 → 跳过（由 authGuard 处理）
 * 2. 路由标记 `meta.permission` 不存在 → 放行
 * 3. 路由标记 `meta.permission` 存在 → 检查权限标识
 *    - `meta.permission` 为字符串：需具备该权限
 *    - `meta.permission` 为数组：具备任一权限即可
 * 4. 无权限 → 重定向到 /403
 *
 * @param router - Vue Router 实例
 */
export function initPermissionGuard(router: Router): void {
  router.beforeEach((to, _from, next) => {
    const userStore = useUserStore()
    const permissionStore = usePermissionStore()

    // 未登录或访客路由，跳过权限检查
    if (!userStore.token || to.meta?.guest) {
      return next()
    }

    // 路由未声明权限要求 → 放行
    const requiredPerm = to.meta?.permission
    if (!requiredPerm) {
      return next()
    }

    // 字符串：单权限；数组：任一权限即可
    const required = Array.isArray(requiredPerm) ? requiredPerm : [requiredPerm]
    const hasPermission = permissionStore.hasAny(...required)

    if (!hasPermission) {
      return next('/403')
    }

    return next()
  })
}
