import type { Router } from 'vue-router'
import { useUserStore } from '@/stores/user'

/**
 * # 认证路由守卫
 *
 * 检查用户是否已登录，未登录则重定向到 /login。
 * 配合 @airpower/web 的 RouterUtil 路由系统共存。
 *
 * ## 检查逻辑
 * 1. 路由标记 `meta.guest` = true（如登录页）→ 直接放行
 * 2. 有 token → 放行
 * 3. 无 token → 重定向到 /login，携带 ?redirect= 参数
 *
 * @param router - Vue Router 实例
 */
export function initAuthGuard(router: Router): void {
  router.beforeEach((to, _from, next) => {
    const userStore = useUserStore()

    // 访客路由（登录页、回调页等）直接放行
    if (to.meta?.guest) {
      return next()
    }

    // 已登录 → 放行
    if (userStore.token) {
      return next()
    }

    // 未登录 → 跳转登录页，记录来源
    return next(`/login?redirect=${encodeURIComponent(to.fullPath)}`)
  })
}
