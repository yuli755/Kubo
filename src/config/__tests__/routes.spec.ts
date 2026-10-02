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

  it('guest 路由仅限登录/法务文本，且一一标注', () => {
    const guestPaths = routes
      .filter(route => route.meta?.guest === true)
      .map(route => route.path)
      .sort()

    // R41 ①a：/privacy、/terms 是协议同意的落点，登录前必须可读（guest 是功能前提，
    // 不是遗漏）。新增业务页仍必须走鉴权 —— 这条断言的存在就是为了让「多标一个」红。
    expect(guestPaths).toEqual(['/login', '/privacy', '/terms'])
  })

  it('业务页 /home 与根布局 console 必须鉴权（无 guest）', () => {
    expect(byPath.get('/home')?.meta?.guest).toBeUndefined()
    expect(byPath.get('/')?.meta?.guest).toBeUndefined()
  })

  it('根路由重定向 /console/index，Console 承载 catchAll 兜底错误页', () => {
    const root = byPath.get('/')
    expect(root?.redirect).toBe('/console/index')
    expect(root?.component).toBeDefined()

    const child = root?.children?.find(route => route.path === ':catchAll(.*)')
    expect(child).toBeDefined()
    expect(child?.component).toBeDefined()
  })

  it('403 页自身不设 guest（否则未授权用户被重定向回 403 造成死循环）', () => {
    expect(byPath.get('/403')?.meta?.guest).toBeUndefined()
  })
})
