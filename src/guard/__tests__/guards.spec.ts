// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * 路由守卫契约测试。
 *
 * 锁的是两条安全主线的边界行为：
 * - 认证守卫：访客路由放行、有 token 放行、无 token 重定向且携带 redirect 回跳；
 * - 权限守卫：未登录跳过（交给认证守卫）、无权限声明放行、字符串/数组权限判定、
 *   无权限重定向 /403。
 */

const userStore = {
  token: '',
}

vi.mock('@/stores/user', () => ({
  useUserStore: () => userStore,
}))

const permissionStore = {
  hasAny: vi.fn(),
}

vi.mock('@/stores/permission', () => ({
  usePermissionStore: () => permissionStore,
}))

const { initAuthGuard } = await import('@/guard/authGuard')
const { initPermissionGuard } = await import('@/guard/permissionGuard')

function makeRouter() {
  const router = { beforeEach: vi.fn() }
  return router as unknown as { beforeEach: ReturnType<typeof vi.fn> }
}

function lastHandler(router: ReturnType<typeof makeRouter>) {
  return router.beforeEach.mock.calls.at(-1)![0]
}

beforeEach(() => {
  userStore.token = ''
  permissionStore.hasAny.mockReset()
})

describe('initAuthGuard · 认证边界', () => {
  it('访客路由直接放行', () => {
    const router = makeRouter()
    initAuthGuard(router as never)
    const next = vi.fn()
    lastHandler(router)({ meta: { guest: true }, fullPath: '/login' }, {}, next)
    expect(next).toHaveBeenCalledWith()
  })

  it('有 token 放行', () => {
    userStore.token = 'token-abc'
    const router = makeRouter()
    initAuthGuard(router as never)
    const next = vi.fn()
    lastHandler(router)({ meta: {}, fullPath: '/dashboard' }, {}, next)
    expect(next).toHaveBeenCalledWith()
  })

  it('无 token 重定向到登录页并携带 redirect', () => {
    const router = makeRouter()
    initAuthGuard(router as never)
    const next = vi.fn()
    lastHandler(router)({ meta: {}, fullPath: '/channel/purchase' }, {}, next)
    expect(next).toHaveBeenCalledWith('/login?redirect=%2Fchannel%2Fpurchase')
  })
})

describe('initPermissionGuard · 权限边界', () => {
  it('未登录跳过权限检查（交给认证守卫）', () => {
    const router = makeRouter()
    initPermissionGuard(router as never)
    const next = vi.fn()
    lastHandler(router)({ meta: { permission: 'a.b.c' }, fullPath: '/x' }, {}, next)
    expect(permissionStore.hasAny).not.toHaveBeenCalled()
    expect(next).toHaveBeenCalledWith()
  })

  it('路由未声明权限 → 放行', () => {
    userStore.token = 't'
    const router = makeRouter()
    initPermissionGuard(router as never)
    const next = vi.fn()
    lastHandler(router)({ meta: {}, fullPath: '/x' }, {}, next)
    expect(permissionStore.hasAny).not.toHaveBeenCalled()
    expect(next).toHaveBeenCalledWith()
  })

  it('字符串权限命中 → 放行', () => {
    userStore.token = 't'
    permissionStore.hasAny.mockReturnValue(true)
    const router = makeRouter()
    initPermissionGuard(router as never)
    const next = vi.fn()
    lastHandler(router)({ meta: { permission: 'a.b.c' }, fullPath: '/x' }, {}, next)
    expect(permissionStore.hasAny).toHaveBeenCalledWith('a.b.c')
    expect(next).toHaveBeenCalledWith()
  })

  it('数组权限任一命中 → 放行', () => {
    userStore.token = 't'
    permissionStore.hasAny.mockReturnValue(true)
    const router = makeRouter()
    initPermissionGuard(router as never)
    const next = vi.fn()
    lastHandler(router)({ meta: { permission: ['a.b', 'a.c'] }, fullPath: '/x' }, {}, next)
    expect(permissionStore.hasAny).toHaveBeenCalledWith('a.b', 'a.c')
    expect(next).toHaveBeenCalledWith()
  })

  it('无权限 → 重定向 /403', () => {
    userStore.token = 't'
    permissionStore.hasAny.mockReturnValue(false)
    const router = makeRouter()
    initPermissionGuard(router as never)
    const next = vi.fn()
    lastHandler(router)({ meta: { permission: 'a.b.c' }, fullPath: '/x' }, {}, next)
    expect(next).toHaveBeenCalledWith('/403')
  })
})
