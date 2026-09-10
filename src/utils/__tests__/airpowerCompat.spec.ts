// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { airpowerPermission, airpowerToken } from '@/utils/airpowerCompat'

/**
 * `airpowerCompat` 契约测试：锁「与 airpower 共享的 localStorage key + 内存态同步」。
 *
 * 首屏写、业务页（懒加载）读的同一份 token/权限，必须落在 airpower 框架能读到的位置：
 *   - token  → `Authorization`（airpower `HttpHeader.AUTHORIZATION`）
 *   - 权限   → `permissions`（airpower `WebConfig.permissionCacheKey`）
 *
 * 权限还有一条**内存态**约定（回归重点）：airpower 的 `PermissionUtil.has()` 读的是
 * 内存静态 `permissionList`（非 localStorage），因此 `saveList` 必须同步调用
 * `PermissionUtil.saveList()` 才能让业务页 ATable/AButton 的权限门禁生效。曾因只写
 * localStorage 导致业务页增删改按钮被整体禁用，此处锁死。
 */

const PermissionUtil = {
  saveList: vi.fn(),
}

vi.mock('@airpower/web', () => ({
  PermissionUtil,
}))

beforeEach(() => {
  localStorage.clear()
  vi.clearAllMocks()
})

describe('airpowerCompat · 与 airpower 共享的 localStorage key', () => {
  it('token 落 Authorization key', () => {
    airpowerToken.save('t-123')
    expect(localStorage.getItem('Authorization')).toBe('t-123')
    expect(airpowerToken.get()).toBe('t-123')
  })

  it('remove 清空 Authorization', () => {
    airpowerToken.save('t-123')
    airpowerToken.remove()
    expect(localStorage.getItem('Authorization')).toBeNull()
    expect(airpowerToken.get()).toBe('')
  })

  it('权限落 permissions key（JSON 数组）', () => {
    airpowerPermission.saveList(['a', 'b'])
    expect(JSON.parse(localStorage.getItem('permissions') || '[]')).toEqual(['a', 'b'])
  })
})

describe('airpowerCompat · 权限内存态同步（防回归）', () => {
  it('saveList 同步 airpower PermissionUtil.saveList（内存态）', async () => {
    airpowerPermission.saveList(['a', 'b'])
    await vi.waitFor(() => expect(PermissionUtil.saveList).toHaveBeenCalledWith(['a', 'b']))
  })
})
