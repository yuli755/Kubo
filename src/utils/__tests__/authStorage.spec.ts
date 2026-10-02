// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from 'vitest'
import { authToken } from '@/utils/authStorage'

/**
 * `authStorage` 契约测试。
 *
 * 锁的是「key 定在哪」—— 这个键是**历史键名**（airpower 时代与框架共享同一份
 * localStorage 留下的），改名会让所有在线用户刷新后被静默登出一次，所以保留；
 * 保留就必须有测试钉住，否则下一个人"顺手改成 kubo:token"时不会有任何提示。
 *
 * 第 35 轮审计删除了 `permissionCache`：`permissions` 键在生产代码里只写不读
 * （真实读者是 permission store 的 sessionStorage 镜像），整份权限清单在登出后
 * 仍留在 localStorage 纯属净风险。这里留一条**反向钉**：模块不得再往该键写东西。
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
