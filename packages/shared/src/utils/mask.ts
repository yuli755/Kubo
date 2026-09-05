import type { MaskPolicy, MaskType } from '../types/mask'

/**
 * 脱敏策略归一化后的前端可执行形式
 *
 * 由 {@link normalizeMaskPolicy} 生成：将后端策略映射为前端
 * `v-mask` 指令可执行的配置；`masked === false` 表示该字段对当前
 * 用户无需脱敏（命中 allowPlaintext 或未匹配任何策略）。
 */
export interface NormalizedMaskPolicy {
  /** 是否需要脱敏 */
  masked: boolean
  /** 脱敏类型（FULL / PARTIAL / REPLACE） */
  maskType?: MaskType
  /** REPLACE 替换表达式 */
  maskExpression?: string
  /** 是否允许明文 */
  allowPlaintext?: boolean
}

/**
 * 归一化后端列级脱敏策略为前端可执行配置
 *
 * 未匹配到策略或策略允许明文时，返回 `{ masked: false }`，
 * 前端据此直接渲染原值。
 *
 * @param policy 后端 my-mask 端点返回的策略（可能为 null）
 * @returns 归一化策略
 */
export function normalizeMaskPolicy(policy: MaskPolicy | null | undefined): NormalizedMaskPolicy {
  if (!policy) {
    return { masked: false }
  }
  if (policy.enabled === false) {
    return { masked: false }
  }
  if (policy.allowPlaintext) {
    return {
      masked: false,
      maskType: policy.maskType,
      maskExpression: policy.maskExpression,
      allowPlaintext: true,
    }
  }
  return {
    masked: true,
    maskType: policy.maskType ?? 'FULL',
    maskExpression: policy.maskExpression,
    allowPlaintext: false,
  }
}

/**
 * 根据归一化策略计算保留明文的位数
 *
 * 后端 PARTIAL 固定保留首尾各 1 位；REPLACE / FULL 不需要保留位。
 *
 * @param policy 归一化策略
 * @returns 保留尾部位数（FULL / REPLACE 返回 0）
 */
export function maskPolicyKeepTail(policy: NormalizedMaskPolicy | null | undefined): number {
  if (!policy?.masked) {
    return 0
  }
  return policy.maskType === 'PARTIAL' ? 1 : 0
}