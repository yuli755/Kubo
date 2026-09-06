import { describe, expect, it } from 'vitest'
import { maskPolicyKeepTail, normalizeMaskPolicy } from '../mask'

/**
 * # 列级脱敏策略归一化 · 单元测试
 *
 * 这组断言是 `v-mask` 与后端 `my-mask` 端点之间的契约：
 * 后端「无策略」与「允许明文」都必须落到 `masked === false`，
 * 否则前端会把「本该明文」的字段画成星号（历史上正是这里的语义分叉导致返工）。
 */
describe('normalizeMaskPolicy', () => {
  it('未命中策略（null / undefined）→ 不脱敏', () => {
    expect(normalizeMaskPolicy(null)).toEqual({ masked: false })
    expect(normalizeMaskPolicy(undefined)).toEqual({ masked: false })
  })

  it('策略被停用 → 不脱敏', () => {
    expect(normalizeMaskPolicy({ maskType: 'FULL', enabled: false }).masked).toBe(false)
  })

  it('允许明文 → 不脱敏，但保留策略内容供 UI 展示"可申请明文"', () => {
    const result = normalizeMaskPolicy({
      maskType: 'PARTIAL',
      maskExpression: 'X',
      allowPlaintext: true,
    })
    expect(result).toMatchObject({ masked: false, maskType: 'PARTIAL', maskExpression: 'X', allowPlaintext: true })
  })

  it('未声明 maskType 时按 FULL 兜底（安全默认：遮蔽优先）', () => {
    expect(normalizeMaskPolicy({ allowPlaintext: false }).maskType).toBe('FULL')
  })

  it('需脱敏时返回 masked=true 且透传 REPLACE 表达式', () => {
    const result = normalizeMaskPolicy({ maskType: 'REPLACE', maskExpression: '已隐藏', allowPlaintext: false })
    expect(result).toMatchObject({ masked: true, maskType: 'REPLACE', maskExpression: '已隐藏', allowPlaintext: false })
  })
})

describe('maskPolicyKeepTail', () => {
  it('FULL / REPLACE 不保留尾位', () => {
    expect(maskPolicyKeepTail({ masked: true, maskType: 'FULL' })).toBe(0)
    expect(maskPolicyKeepTail({ masked: true, maskType: 'REPLACE' })).toBe(0)
  })

  it('PARTIAL 保留 1 位（与后端 ColumnMaskingService.maskPartial 一致）', () => {
    expect(maskPolicyKeepTail({ masked: true, maskType: 'PARTIAL' })).toBe(1)
  })

  it('空策略 → 0', () => {
    expect(maskPolicyKeepTail(null)).toBe(0)
    expect(maskPolicyKeepTail({ masked: false, maskType: 'PARTIAL' })).toBe(0)
  })
})
