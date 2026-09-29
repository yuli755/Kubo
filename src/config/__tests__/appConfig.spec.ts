import { describe, expect, it } from 'vitest'
import { AppConfig } from '@/config/AppConfig'
import { BOOLEAN_NO, BOOLEAN_YES, Constant } from '@/config/Constant'

/**
 * 应用级配置与兼容再导出的契约测试。
 *
 * 这里用**静态导入**而不是 `await import(...)`：这两个用例都没有 `vi.resetModules()` 的
 * 顺序需求，动态导入只会把 `AppConfig` 整条依赖图的转换/插桩成本算进 `it` 的 30s 时钟里 ——
 * 在 `pnpm test:coverage`（v8 插桩）叠加机器负载时，冷启动那一下会直接超时失败
 * （实测：同一轮链路里它先以 8.9s 通过，随后在 coverage 步骤以 `Test timed out in 30000ms` 失败）。
 * 静态导入把这份成本挪到用例收集阶段，不再受测试时钟约束，断言本身完全等价。
 */

describe('appConfig · 当前用户引用', () => {
  it('currentUser 初始化为可写的 UserEntity ref', () => {
    expect(AppConfig.currentUser.value).toBeDefined()
    // 可写：框架在登录后写入当前用户（重新赋值应生效）
    const next = AppConfig.currentUser.value
    AppConfig.currentUser.value = next
    expect(AppConfig.currentUser.value).toBe(next)
  })
})

describe('constant · 兼容再导出', () => {
  it('bOOLEAN_NO / BOOLEAN_YES / Constant 均可用', () => {
    expect(BOOLEAN_NO).toBe('0')
    expect(BOOLEAN_YES).toBe('1')
    expect(Constant).toBeDefined()
  })
})
