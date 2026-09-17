// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from 'vitest'
import { authToken, permissionCache } from '@/utils/authStorage'

/**
 * `authStorage` 契约测试。
 *
 * 锁的是「key 定在哪」—— 这两个键是**历史键名**（airpower 时代与框架共享同一份
 * localStorage 留下的），改名会让所有在线用户刷新后被静默登出一次，所以保留；
 * 保留就必须有测试钉住，否则下一个人"顺手改成 kubo:token"时不会有任何提示。
 */
beforeEach(() => {
  localStorage.clear()
})

describe('authStorage · token', () => {
  it('save/get/remove 都落在 Authorization 键上', () => {
    authToken.save('t-123')
    expect(localStorage.getItem('Authorization')).toBe('t-123')
    expect(authToken.get()).toBe('t-123')

    authToken.remove()
    expect(localStorage.getItem('Authorization')).toBeNull()
    expect(authToken.get()).toBe('')
  })

  it('未登录时 get 返回空串而不是 null（调用方不必判 null）', () => {
    expect(authToken.get()).toBe('')
  })
})

describe('authStorage · 权限缓存', () => {
  it('以 JSON 数组落在 permissions 键上，且能读回', () => {
    permissionCache.saveList(['a', 'b'])
    expect(JSON.parse(localStorage.getItem('permissions') || '[]')).toEqual(['a', 'b'])
    expect(permissionCache.get()).toEqual(['a', 'b'])
  })

  it('缓存被写坏时 get 退回空数组（不让一个坏键把守卫打崩）', () => {
    localStorage.setItem('permissions', 'not-json')
    expect(permissionCache.get()).toEqual([])
  })
})
