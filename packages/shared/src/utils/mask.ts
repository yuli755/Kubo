import type { MaskPolicy, MaskType } from '../types/mask'

/**
 * 后端 `applyMask` 的兜底遮蔽值（`maskExpression` 缺省时使用）
 */
const DEFAULT_MASK = '****'

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
 * ### 判断一个值是否"已经是遮蔽结果"
 *
 * 后端 enforce 模式下 `SensitiveFieldSerializer` 已经把值写成掩码字符串，
 * 前端若再按策略遮一次就会得到 `1******0` 这类二次破坏（`039****88` → PARTIAL → `0**8`）。
 * 因此渲染前必须过这道判断，命中则原样返回。
 *
 * 判定口径（保守，宁可少遮一次也不产生错误文本）：
 * - 连续两个及以上 `*`：真实手机号 / 价格 / 身份证里不会出现
 * - `h:` + 十六进制：后端 `HASH` 的产出格式
 * - 整串只有一个字符且是 `*`
 */
export function looksMasked(value: unknown): boolean {
  if (typeof value !== 'string' || value.length === 0) {
    return false
  }
  return /\*\*/.test(value) || /^h:[0-9a-f]{6,}$/i.test(value) || /^\*+$/.test(value)
}

/**
 * 按后端 `ColumnMaskingService#maskPartial` 的口径做部分遮蔽
 *
 * 保留首尾各 1 个字符，中间最多 6 个星号（Java 里是 `Math.min(len - 2, 6)`，
 * 不是"全部替换"——长度不同的字段遮出来的星号数刻意保持一致，避免泄漏长度信息）。
 */
function maskPartial(value: string): string {
  if (value.length <= 2) {
    return '**'
  }
  return `${value.charAt(0)}${'*'.repeat(Math.min(value.length - 2, 6))}${value.charAt(value.length - 1)}`
}

/**
 * 按后端 `maskLast4` 的口径只保留末四位
 */
function maskLast4(value: string, expression?: string): string {
  if (value.length <= 4) {
    return expression || DEFAULT_MASK
  }
  return `${'*'.repeat(value.length - 4)}${value.slice(-4)}`
}

/**
 * ### 按后端策略计算遮蔽结果（与 Java `applyMask` 逐分支对齐）
 *
 * 用途：`shadow` 阶段后端返回明文、由前端按同一套策略遮蔽；以及前端需要在
 * 明文可渲染的地方（如导出预览、复制）复用同一算法。
 *
 * 与后端一致的三处"反直觉"口径，改动前请先读注释：
 * 1. `FULL` / `REPLACE` **完全忽略原值**（含长度），只输出表达式 —— 避免泄漏长度；
 * 2. 未知 / 缺省类型 → `****`，**绝不回退原值**（安全失败）；
 * 3. `HASH` 依赖 SHA-256，浏览器端同步实现不可靠，因此**不做本地计算**，
 *    直接落到最严格遮蔽：能显示真值时后端已经写好 `h:xxxx`，由 {@link looksMasked} 原样透传。
 *
 * @param value 原始值（数字也会被转成字符串，与后端 `value.toString()` 一致）
 * @param policy `my-mask` 返回的策略；`null` / 停用 / 允许明文 → 返回原值
 * @returns 遮蔽后的字符串（或原值）
 */
export function applyMaskPolicy(value: unknown, policy: MaskPolicy | null | undefined): unknown {
  if (value === null || value === undefined) {
    return value
  }
  if (!policy || policy.enabled === false || policy.allowPlaintext) {
    return value
  }
  const strValue = String(value)
  if (looksMasked(strValue)) {
    // 已经是遮蔽结果（后端 enforce 已处理），不重复遮蔽
    return strValue
  }
  const expression = policy.maskExpression || undefined
  switch ((policy.maskType ?? 'FULL').toUpperCase()) {
    case 'FULL':
    case 'REPLACE':
      return expression || DEFAULT_MASK
    case 'PARTIAL':
      return maskPartial(strValue)
    case 'LAST4':
      return maskLast4(strValue, expression)
    case 'HASH':
      // 前端不实现摘要算法：宁可全遮，也不要显示一个与后端不同口径的伪摘要
      return DEFAULT_MASK
    default:
      return DEFAULT_MASK
  }
}
