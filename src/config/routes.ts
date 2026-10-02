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
    component: () => import('@/view/login.vue'),
  },
  // R41 ①a：登录页协议死链（/privacy、/terms）的落点。guest 可读是本页存在的理由——
  // 「同意一个读不到的文本」比不同意更糟；authGuard 对 guest 双向放行，登录后也能看。
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
    path: '/home',
    name: '首页',
    redirect: '/console/index',
  },
  {
    path: '/403',
    name: '无权限',
    component: () => import('@/view/error.vue'),
  },
  {
    path: '/',
    name: 'console',
    redirect: '/console/index',
    component: () => import('@/component/Console.vue'),
    children: [
      {
        /**
         * `/console` 是「进入控制台」的入口地址（登录后回跳、门户页按钮、文档里都这么写），
         * 但它本身不是任何页面：动态菜单路由都是 `/console/.../list`。
         * 没有这条重定向时它会被下面的 `:catchAll(.*)` 接住 → 用户看到 404 页。
         * 入口地址必须显式重定向。
         */
        path: 'console',
        name: 'console-entry',
        redirect: '/console/index',
      },
      {
        path: ':catchAll(.*)',
        component: () => import('@/view/error.vue'),
      },
    ],
  },
]
