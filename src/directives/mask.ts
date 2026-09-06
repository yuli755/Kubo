import type { MaskPolicy } from '@kubo/shared'
import type { Directive, DirectiveBinding } from 'vue'
import { applyMaskPolicy } from '@kubo/shared'
import { ensureMaskPolicy, maskPolicyEntry } from '@/composables/useMaskPolicy'

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
   * ### 数据资源标识（如 `channel.purchasePrice`）
   *
   * 与 `field` 一起提供时，遮蔽口径**由后端 `my-mask` 策略决定**（唯一权威），
   * 本地 `type` 只在策略到达前作为保守兜底；不写这两个字段时才是纯前端遮蔽
   * （演示数据、或该展示位没有对应 `data_resource` 时使用）。
   */
  resourceKey?: string
  /** 字段名（如 `price`），需与后端实体属性名一致 */
  field?: string
  /**
   * ### 已解析好的策略（可选）
   *
   * 页面已通过 `useMaskPolicy` 拿到策略时直接传入，指令不再自行发起请求。
   * 传 `null` 表示"后端确认无需脱敏"，此时渲染原值。
   */
  policy?: MaskPolicy | null
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

type MaskElement = HTMLElement & { _maskBinding?: DirectiveBinding<MaskBinding> }

/**
 * ### 应用脱敏到元素
 *
 * 解析顺序（**后端策略优先**，这是 F2 的关键约定）：
 * 1. 绑定了 `policy`（页面已取到）或 `resourceKey` + `field`（命中缓存）→ 用后端策略算法；
 * 2. 策略还没到 → 保守遮蔽：有本地 `type` 就按本地规则遮，没有就输出 `****`，
 *    **绝不因为"还没查到策略"而闪出明文**；同时发起拉取，到齐后重渲染；
 * 3. 拉取失败 → 按最严格处理（`****`），因为"失败"不等于"无需脱敏"；
 * 4. 完全没接策略（无 resourceKey/field）→ 纯本地规则。
 */
function applyMask(el: HTMLElement, binding: DirectiveBinding<MaskBinding>): void {
  const target = el as MaskElement
  target._maskBinding = binding
  const { type, value, mask, fallback = '****', keepStart, keepEnd, resourceKey, field, policy } = binding.value ?? {}

  const write = (next: unknown): void => {
    el.textContent = next === null || next === undefined || next === '' ? fallback : String(next)
  }

  if (value === undefined || value === null) {
    write(fallback)
    return
  }

  const local = (): string => maskValue(String(value), { type, mask, keepStart, keepEnd })

  if (policy !== undefined) {
    write(applyMaskPolicy(value, policy))
    return
  }
  if (!resourceKey || !field) {
    write(local())
    return
  }

  const entry = maskPolicyEntry(resourceKey, field)
  if (!entry || (!entry.loaded && !entry.failed)) {
    ensureMaskPolicy(resourceKey, field).then(() => {
      // 策略到齐后重渲染。判据是"这个元素上挂的还是同一个 binding"：
      // 组件已卸载时 unmounted 钩子会把引用清掉，异步回调因此不会写到死元素上
      // （不用 isConnected：测试与 teleport/keep-alive 场景里元素本来就可能是 detached 的）
      if (target._maskBinding === binding) {
        applyMask(target, binding)
      }
    })
    write(type ? local() : fallback)
    return
  }
  if (entry.failed) {
    write(fallback)
    return
  }
  write(applyMaskPolicy(value, entry.policy))
}

/**
 * # 脱敏指令 v-mask
 *
 * 将文本内容按规则脱敏（手机号 / 身份证 / 银行卡 / 邮箱 / 姓名 / 自定义）。
 *
 * ## 使用示例
 * ```html
 * <!-- 按后端策略脱敏（推荐：口径与后端一致，前端不自己决定遮多少） -->
 * <span v-mask="{ value: row.price, resourceKey: 'channel.purchasePrice', field: 'price' }">
 *   {{ row.price }}
 * </span>
 *
 * <!-- 页面已取到策略时直接传，省一次请求 -->
 * <span v-mask="{ value: row.price, policy: policyOf('price') }">{{ row.price }}</span>
 *
 * <!-- 纯前端遮蔽（演示数据 / 该字段没有 data_resource 时）：手机号 138****8000 -->
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
  unmounted(el: HTMLElement): void {
    // 切断异步回调与已卸载元素的联系
    ;(el as MaskElement)._maskBinding = undefined
  },
}
