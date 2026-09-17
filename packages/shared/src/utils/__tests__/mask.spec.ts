import type { MaskPolicy, MaskType } from '../../types/mask'
import { describe, expect, it } from 'vitest'
import { applyMaskPolicy, looksMasked, normalizeMaskPolicy } from '../mask'

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

/**
 * # applyMaskPolicy · 与后端 `ColumnMaskingService#applyMask` 的逐分支对账
 *
 * 这些期望值是**按 Java 源码逐行推导**的，不是"看起来合理"：
 * FULL/REPLACE 忽略原值只输出表达式；PARTIAL 星号数封顶 6（避免泄漏长度）；
 * LAST4 长度不足 4 时退回表达式；未知类型安全失败成 `****`。
 * 后端改了算法而这里变红，就是提醒"前端必须同步"，反之亦然。
 */
describe('applyMaskPolicy', () => {
  it('fULL / REPLACE 忽略原值，只输出表达式（缺省 ****）', () => {
    expect(applyMaskPolicy(12345.67, { maskType: 'FULL' })).toBe('****')
    expect(applyMaskPolicy('13800138000', { maskType: 'FULL', maskExpression: '已隐藏' })).toBe('已隐藏')
    expect(applyMaskPolicy('x', { maskType: 'REPLACE' })).toBe('****')
  })

  it('pARTIAL 保留首尾各 1 位，中间星号数封顶 6', () => {
    // Java: value.charAt(0) + "*".repeat(Math.min(len - 2, 6)) + value.charAt(len - 1)
    expect(applyMaskPolicy('13800138000', { maskType: 'PARTIAL' })).toBe('1******0')
    expect(applyMaskPolicy('1999.5', { maskType: 'PARTIAL' })).toBe('1****5')
    expect(applyMaskPolicy('abc', { maskType: 'PARTIAL' })).toBe('a*c')
    expect(applyMaskPolicy('ab', { maskType: 'PARTIAL' })).toBe('**')
    expect(applyMaskPolicy('a', { maskType: 'PARTIAL' })).toBe('**')
  })

  it('lAST4 只留末四位；长度不足时用表达式或 ****', () => {
    expect(applyMaskPolicy('13800138000', { maskType: 'LAST4' })).toBe('*******8000')
    expect(applyMaskPolicy('1234', { maskType: 'LAST4' })).toBe('****')
    expect(applyMaskPolicy('12', { maskType: 'LAST4', maskExpression: '不可见' })).toBe('不可见')
  })

  it('hASH 不在前端本地计算，一律最严格遮蔽（后端已算好的值由幂等守卫透传）', () => {
    expect(applyMaskPolicy('secret-value', { maskType: 'HASH' })).toBe('****')
    expect(applyMaskPolicy('h:ab12cd34ef56', { maskType: 'HASH' })).toBe('h:ab12cd34ef56')
  })

  it('类型大小写不敏感；未知类型安全失败为 ****，绝不回退原值', () => {
    expect(applyMaskPolicy('13800138000', { maskType: 'partial' as MaskType })).toBe('1******0')
    expect(applyMaskPolicy('13800138000', { maskType: 'UNKNOWN' as MaskType })).toBe('****')
    expect(applyMaskPolicy('13800138000', {} as MaskPolicy)).toBe('****')
  })

  it('无策略 / 停用 / 允许明文 → 原值；null 值原样返回', () => {
    expect(applyMaskPolicy(1999.5, null)).toBe(1999.5)
    expect(applyMaskPolicy(1999.5, { maskType: 'FULL', enabled: false })).toBe(1999.5)
    expect(applyMaskPolicy(1999.5, { maskType: 'FULL', allowPlaintext: true })).toBe(1999.5)
    expect(applyMaskPolicy(null, { maskType: 'FULL' })).toBeNull()
    expect(applyMaskPolicy(undefined, { maskType: 'FULL' })).toBeUndefined()
  })

  it('已遮蔽的值不重复遮蔽（enforce 模式下后端已写好掩码）', () => {
    expect(applyMaskPolicy('039****88', { maskType: 'PARTIAL' })).toBe('039****88')
    expect(applyMaskPolicy('****', { maskType: 'PARTIAL' })).toBe('****')
    // 单个星号不算"已遮蔽"，避免把 a*b 这类真实文本误判
    expect(looksMasked('a*b')).toBe(false)
    expect(looksMasked('13800138000')).toBe(false)
  })
})
