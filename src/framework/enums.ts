/**
 * # 框架层·枚举与颜色
 *
 * 枚举字典是「字段 → 展示」的关键：`@Field({ dictionary: XxxEnum })` 让表格列渲染成带色标签、
 * 表单渲染成下拉选项。这里实现与原 `WebEnum` 一致的用法：
 *
 * ```ts
 * export class InputStatusEnum extends WebEnum {
 *   static AUDITING = new InputStatusEnum(1, '审核中').setColor(WebColor.WARNING)
 * }
 * InputStatusEnum.getList()        // 下拉选项
 * InputStatusEnum.getLabel(row.status)  // 表格标签
 * InputStatusEnum.AUDITING.equalsKey(row.status)
 * ```
 */

import type { EnumKey } from './types'

/** 语义色（与 Element Plus 的 success/warning/danger/info 对齐） */
export enum WebColor {
  SUCCESS = '#67C23A',
  WARNING = '#E6A23C',
  DANGER = '#F56C6C',
  NORMAL = '#909399',
}

/** 枚举基类 */
export class WebEnum<K extends EnumKey = number> {
  /** 枚举值（后端返回的就是它） */
  readonly key: K

  /** 展示文案 */
  label?: string

  /** 标签颜色 */
  color?: string

  /** 是否禁用该选项 */
  disabled?: boolean

  constructor(key: K, label?: string) {
    this.key = key
    if (label !== undefined) {
      this.label = label
    }
  }

  /** 设置颜色（链式） */
  setColor(color: string): this {
    this.color = color
    return this
  }

  /** 设置禁用（链式） */
  setDisabled(disabled = true): this {
    this.disabled = disabled
    return this
  }

  /** 设置文案（链式） */
  setLabel(label: string): this {
    this.label = label
    return this
  }

  /** 取文案 */
  getLabel(): string {
    return this.label ?? String(this.key)
  }

  /** 取颜色 */
  getColor(): string | undefined {
    return this.color
  }

  /** key 是否相等 */
  equalsKey(key: unknown): boolean {
    return this.key === key
  }

  /** 选项数组（按类静态属性收集） */
  static getList(): WebEnum[] {
    const Class = this as unknown as Record<string, unknown>
    return Object.values(Class).filter((item): item is WebEnum => item instanceof (this as unknown as new () => WebEnum))
  }

  /** 按 key 查选项 */
  static get(key: unknown): WebEnum | undefined {
    return this.getList().find(item => item.key === key)
  }

  /** 按 key 取文案（缺省 `-`） */
  static getLabel(key: unknown, defaultLabel = '-'): string {
    return this.get(key)?.getLabel() || defaultLabel
  }

  /** 按 key 取颜色 */
  static getColor(key: unknown): string | undefined {
    return this.get(key)?.getColor()
  }

  /** 别名：原库叫 `toArray()` */
  static toArray(): WebEnum[] {
    return this.getList()
  }
}

/**
 * # 脱敏类型
 *
 * 与后端列级脱敏策略同名，前端用于「列表里也能安全展示部分信息」的场景。
 */
export class DesensitizeType {
  /** 姓名：张三 → 张 */
  static CHINESE_NAME = new DesensitizeType('chineseName', (value: string) => (value.length > 1 ? `${value[0]}${'*'.repeat(value.length - 1)}` : value))

  /** 手机号：138****8888 */
  static MOBILE = new DesensitizeType('mobile', (value: string) => (value.length >= 7 ? `${value.slice(0, 3)}****${value.slice(-4)}` : value))

  /** 邮箱：ab***@example.com */
  static EMAIL = new DesensitizeType('email', (value: string) => {
    const [name, domain] = value.split('@')
    if (!domain) {
      return value
    }
    return `${name.slice(0, 2)}***@${domain}`
  })

  /** 身份证：保留前 6 后 4 */
  static ID_CARD = new DesensitizeType('idCard', (value: string) => (value.length > 10 ? `${value.slice(0, 6)}********${value.slice(-4)}` : value))

  private constructor(
    readonly key: string,
    private readonly mask: (value: string) => string,
  ) {}

  /** 作用于文本 */
  desensitize(value?: string | null): string {
    if (!value) {
      return ''
    }
    return this.mask(String(value))
  }
}

/** 日期控件类型（原库同名枚举） */
export enum DateTimeType {
  DATETIME = 'datetime',
  DATE = 'date',
  TIME = 'time',
  WEEK = 'week',
  YEAR = 'year',
  MONTH = 'month',
}
