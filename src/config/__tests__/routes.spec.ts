import { describe, expect, it } from 'vitest'
import { routes } from '@/config/routes'

/**
 * 路由表契约测试：锁「哪些路由是访客可进、哪些必须鉴权」。
 *
 * 这里不是测 vue-router 本身，而是锁 authGuard / permissionGuard 依赖的 meta 约定：
 * `meta.guest: true` 是访客放行的唯一依据 —— 少标一个（登录/回调/绑定/授权），
 * 未登录用户会被 authGuard 重定向；多标一个业务页（home/console），
 * 就等于把需要鉴权的页面暴露给了未登录访客。两条都会在运行时才炸，且方向相反。
 */

describe('routes · 访客路由 meta 契约', () => {
  const byPath = new Map(routes.map(route => [route.path, route]))

  it('guest 路由仅限登录/回调/绑定/授权 + 静态法务页，且一一标注', () => {
    const guestPaths = routes
      .filter(route => route.meta?.guest === true)
      .map(route => route.path)
      .sort()

    // G-1：/privacy /terms /forgot-password 是登录页入口对应的静态法务/帮助页，
    // 无业务数据、不含鉴权依赖，允许访客访问；新增 guest 路由必须在此显式登记。
    expect(guestPaths).toEqual([
      '/authorize',
      '/bind/:platform',
      '/callback/:platform',
      '/forgot-password',
      '/login',
      '/privacy',
      '/terms',
    ])
  })

  it('业务页 /home 与根布局 console 必须鉴权（无 guest）', () => {
    expect(byPath.get('/home')?.meta?.guest).toBeUndefined()
    expect(byPath.get('/')?.meta?.guest).toBeUndefined()
  })

  it('根路由重定向 /home，Console 承载 catchAll 兜底错误页', () => {
    const root = byPath.get('/')
    expect(root?.redirect).toBe('/home')
    expect(root?.component).toBeDefined()

    const child = root?.children?.find(route => route.path === ':catchAll(.*)')
    expect(child).toBeDefined()
    expect(child?.component).toBeDefined()
  })

  it('403 页自身不设 guest（否则未授权用户被重定向回 403 造成死循环）', () => {
    expect(byPath.get('/403')?.meta?.guest).toBeUndefined()
  })
})
