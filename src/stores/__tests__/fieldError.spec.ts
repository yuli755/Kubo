import { beforeEach, describe, expect, it } from 'vitest'
import { clearFieldError, fieldError, publishFieldError } from '@/stores/fieldError'

/**
 * # 出错字段定位信号（`stores/fieldError.ts`）
 *
 * 这是「拦截器解析出字段名」与「表单把出错项滚到视野内」之间的信箱。
 * 两条契约最关键：
 * - **没有字段名就不发布**（发布一个 `field: ''` 会让消费方白白滚动/清校验）；
 * - **消费后必须能清空**，否则下次打开表单会被上一次的旧信号误触发。
 */

describe('fieldError · 发布与清空', () => {
  beforeEach(() => {
    clearFieldError()
  })

  it('初始为 null（没有待处理的定位信号）', () => {
    expect(fieldError.value).toBeNull()
  })

  it('发布后带上字段名、原文与时间戳', () => {
    const before = Date.now()
    publishFieldError('email', 'email: 邮箱格式不正确')

    expect(fieldError.value).not.toBeNull()
    expect(fieldError.value?.field).toBe('email')
    expect(fieldError.value?.message).toBe('email: 邮箱格式不正确')
    expect(fieldError.value?.at).toBeGreaterThanOrEqual(before)
    expect(fieldError.value?.at).toBeLessThanOrEqual(Date.now())
  })

  it('字段名为空/未解析出来时不发布（宁可不动，也不要乱跳焦点）', () => {
    publishFieldError(undefined, '服务器内部错误')
    expect(fieldError.value).toBeNull()

    publishFieldError('', '服务器内部错误')
    expect(fieldError.value).toBeNull()
  })

  it('后一次发布覆盖前一次（用户看到的是最新那次的错误字段）', () => {
    publishFieldError('email', 'email: 不能为空')
    publishFieldError('name', 'name: 长度超限')
    expect(fieldError.value?.field).toBe('name')
  })

  it('清空后回到 null，消费方可安全重复消费', () => {
    publishFieldError('code', 'code: 编码重复')
    clearFieldError()
    expect(fieldError.value).toBeNull()
    // 再次清空不应报错（消费方不必判断是否已清）
    clearFieldError()
    expect(fieldError.value).toBeNull()
  })
})
