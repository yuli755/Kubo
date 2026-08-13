import type { Directive, DirectiveBinding } from 'vue'

/**
 * # 脱敏掩码类型
 */
export type MaskType = 'phone' | 'idCard' | 'bankCard' | 'email' | 'name' | 'custom'

/**
 * # 自定义脱敏函数
 *
 * 接收原始字符串，返回脱敏后的字符串。
 */
export type MaskFunction = (value: string) => string

/**
 * `v-mask` 指令的绑定值
 */
export interface MaskBinding {
  /** 脱敏类型 */
  type: MaskType
  /** 原始值 */
  value: string | number
  /**
   * ### 自定义脱敏函数
   *
   * 仅当 `type: 'custom'` 时生效。
   */
  mask?: MaskFunction
  /**
   * ### 兜底文本
   *
   * 值为空或脱敏失败时显示。默认 `****`。
   */
  fallback?: string
  /**
   * ### 保留首部字符数
   *
   * 对 `custom` / `name` 等类型生效，控制明文前缀长度。
   */
  keepStart?: number
  /**
   * ### 保留尾部字符数
   *
   * 对 `custom` / `name` 等类型生效，控制明文后缀长度。
   */
  keepEnd?: number
}

function maskPhone(value: string): string {
  // 不足 7 位无法脱敏，直接保留首 3 + 末 4 中的可见部分
  if (value.length < 7) {
    return '*'.repeat(value.length)
  }
  return value.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')
}

function maskIdCard(value: string): string {
  if (value.length < 10) {
    return '*'.repeat(value.length)
  }
  return value.replace(/(\d{6})\d{8}(\d{4})/, '$1********$2')
}

function maskBankCard(value: string): string {
  if (value.length < 8) {
    return '*'.repeat(value.length)
  }
  return value.replace(/(\d{4})\d{8,12}(\d{4})/, '$1 **** **** $2')
}

function maskEmail(value: string): string {
  const [name, domain] = value.split('@')
  if (!domain) {
    return '*'.repeat(Math.min(value.length, 4))
  }
  const visible = name.slice(0, Math.min(2, name.length))
  return `${visible}***@${domain}`
}

/**
 * ### 通用字符脱敏
 *
 * 保留首尾 `keepStart` / `keepEnd` 位明文，中间替换为 `*`。
 */
function maskGeneric(value: string, keepStart: number, keepEnd: number): string {
  const len = value.length
  if (len <= keepStart + keepEnd) {
    // 长度不足以保留首尾，全部脱敏但至少保留 1 位明文
    return len > 0 ? `${value[0]}${'*'.repeat(len - 1)}` : ''
  }
  const head = value.slice(0, keepStart)
  const tail = value.slice(len - keepEnd)
  const maskedCount = len - keepStart - keepEnd
  return `${head}${'*'.repeat(maskedCount)}${tail}`
}

/**
 * ### 姓名脱敏
 *
 * 两字姓名保留姓，三字及以上保留首尾。
 */
function maskName(value: string): string {
  const len = value.length
  if (len <= 1) {
    return value
  }
  if (len === 2) {
    return `${value[0]}*`
  }
  return maskGeneric(value, 1, 1)
}

/**
 * 根据类型对值进行脱敏
 *
 * @param value 原始字符串
 * @param binding 脱敏配置（含 type 及可选的 mask/keepStart/keepEnd）
 */
export function maskValue(value: string, binding: Pick<MaskBinding, 'type' | 'mask' | 'keepStart' | 'keepEnd'>): string {
  switch (binding.type) {
    case 'phone':
      return maskPhone(value)
    case 'idCard':
      return maskIdCard(value)
    case 'bankCard':
      return maskBankCard(value)
    case 'email':
      return maskEmail(value)
    case 'name':
      return maskName(value)
    case 'custom':
      return binding.mask ? binding.mask(value) : maskGeneric(value, binding.keepStart ?? 1, binding.keepEnd ?? 1)
    default:
      return value
  }
}

/**
 * ### 应用脱敏到元素
 */
function applyMask(el: HTMLElement, binding: DirectiveBinding<MaskBinding>): void {
  const { type, value, mask, fallback = '****', keepStart, keepEnd } = binding.value ?? {}
  if (value === undefined || value === null) {
    el.textContent = fallback
    return
  }
  const raw = String(value)
  const masked = maskValue(raw, { type, mask, keepStart, keepEnd })
  el.textContent = masked || fallback
}

/**
 * # 脱敏指令 v-mask
 *
 * 将文本内容按规则脱敏（手机号 / 身份证 / 银行卡 / 邮箱 / 姓名 / 自定义）。
 *
 * ## 使用示例
 * ```html
 * <!-- 手机号：138****8000 -->
 * <span v-mask="{ type: 'phone', value: user.phone }">13800138000</span>
 *
 * <!-- 姓名：张*三 -->
 * <span v-mask="{ type: 'name', value: user.nickname }">张三</span>
 *
 * <!-- 自定义脱敏函数 -->
 * <span v-mask="{ type: 'custom', value: code, mask: v => v.slice(0, 2) + '***' }">ABC123</span>
 *
 * <!-- 通用首尾保留 -->
 * <span v-mask="{ type: 'custom', value: id, keepStart: 4, keepEnd: 2 }">1234567890</span>
 * ```
 *
 * @author Hamm.cn
 */
export const vMask: Directive<HTMLElement, MaskBinding> = {
  mounted: applyMask,
  updated: applyMask,
}
