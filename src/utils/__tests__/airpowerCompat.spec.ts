// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from 'vitest'
import { airpowerPermission, airpowerToken } from '@/utils/airpowerCompat'

/**
 * `airpowerCompat` 契约测试：锁「与 airpower 共享的 localStorage key」这一关键约定。
 *
 * 首屏写、业务页（懒加载）读的同一份 token/权限，必须落在 airpower 框架能读到的 key：
 *   - token  → `Authorization`（airpower `HttpHeader.AUTHORIZATION`）
 *   - 权限   → `permissions`（airpower `WebConfig.permissionCacheKey`）
 * key 一旦漂移，业务页 airpower 组件（AImage/AUpload 拼 URL、权限缓存）将读不到数据。
 */
describe('airpowerCompat · 与 airpower 共享的 localStorage key', () => {
  beforeEach(() => {
    localStorage.clear()
  })

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
