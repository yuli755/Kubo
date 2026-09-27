import type { RouteRecordRaw } from 'vue-router'

/**
 * # 路由配置
 * @author Hamm.cn
 *
 * ## 路由 meta 说明
 * - `meta.guest: true` — 访客路由（登录页、回调页等），authGuard 直接放行
 * - `meta.permission: string | string[]` — 访问该路由所需权限标识，
 *   字符串为单权限，数组为任一权限即可。permissionGuard 检查不通过则跳转 /403
 */
export const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: '登录',
    meta: { guest: true },
    // 如需启用Oauth2登录 请使用 @airpower/view/login.vue
    component: () => import('@/view/login.vue'),
  },
  {
    path: '/callback/:platform',
    name: 'Oauth2登录回调',
    meta: { guest: true },
    component: () => import('@/view/callback.vue'),
  },
  {
    path: '/bind/:platform',
    name: '绑定',
    meta: { guest: true },
    component: () => import('@/view/bind.vue'),
  },
  {
    path: '/home',
    name: '首页',
    component: () => import('@/view/home.vue'),
  },
  {
    path: '/authorize',
    name: '授权',
    meta: { guest: true },
    component: () => import('@/view/authorize.vue'),
  },
  {
    path: '/403',
    name: '无权限',
    component: () => import('@/view/error.vue'),
  },
  // G-1：登录页三个静态入口（隐私政策/服务条款/忘记密码）。访客路由，
  // 共用 legal.vue；不注册时这三个 href 会落进 Console 的 catchAll 错误页。
  {
    path: '/privacy',
    name: '隐私政策',
    meta: { guest: true },
    component: () => import('@/view/legal.vue'),
  },
  {
    path: '/terms',
    name: '服务条款',
    meta: { guest: true },
    component: () => import('@/view/legal.vue'),
  },
  {
    path: '/forgot-password',
    name: '忘记密码',
    meta: { guest: true },
    component: () => import('@/view/legal.vue'),
  },
  {
    path: '/',
    name: 'console',
    redirect: '/home',
    component: () => import('@/component/Console.vue'),
    children: [
      {
        path: ':catchAll(.*)',
        component: () => import('@/view/error.vue'),
      },
    ],
  },
]
