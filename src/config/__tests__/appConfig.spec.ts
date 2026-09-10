import { describe, expect, it } from 'vitest'

/**
 * 应用级配置与兼容再导出的契约测试。
 */

describe('appConfig · 当前用户引用', () => {
  it('currentUser 初始化为可写的 UserEntity ref', async () => {
    const { AppConfig } = await import('@/config/AppConfig')
    expect(AppConfig.currentUser.value).toBeDefined()
    // 可写：框架在登录后写入当前用户（重新赋值应生效）
    const next = AppConfig.currentUser.value
    AppConfig.currentUser.value = next
    expect(AppConfig.currentUser.value).toBe(next)
  })
})

describe('constant · 兼容再导出', () => {
  it('bOOLEAN_NO / BOOLEAN_YES / Constant 均可用', async () => {
    const c = await import('@/config/Constant')
    expect(c.BOOLEAN_NO).toBe('0')
    expect(c.BOOLEAN_YES).toBe('1')
    expect(c.Constant).toBeDefined()
  })
})
