/**
 * # 框架层·单元格渲染
 *
 * 「一个字段在表格/详情里长什么样」原来是 `ATable` 内部的事，散落在组件里的后果是：
 * 表格能格式化、详情页不能、导出又是第三套。这里抽成纯函数，三处共用。
 *
 * 判定顺序（先命中先返回）：
 * `formatter` → 空值 `-` → 字典 label → 日期 → 脱敏 → 关联对象 → 金额 → 布尔 → 数组 → 字符串
 */

import type { ITableColumn, IWebEnumConstructor } from './types'
import { DateTimeFormatter } from './datetime'
import { DesensitizeType, getEnumColor, getEnumLabel } from './enums'
import { formatMoney } from './format'

/** 单元格渲染配置：`@Table` / `@Field` 的配置子集，详情页可以只传一个字段名 */
export interface ICellConfig {
  key: string
  /** 自定义取值：`(row, value) => 值` */
  formatter?: (row: never, value?: unknown) => unknown
  /** 金额列（`true` = 两位小数，也可传小数位 / `false` 表示不补零） */
  money?: boolean | number
  /** 按时间格式化（`true` = `YYYY-MM-DD HH:mm:ss`，也可传 `DateTimeFormatter`） */
  datetime?: boolean | unknown
  /** 脱敏规则（`DesensitizeType`） */
  desensitize?: unknown
  /** 关联对象列（渲染 `getPayloadLabel()` / `name`） */
  payload?: boolean
  /** 电话列（按手机号脱敏展示） */
  phone?: boolean
}

function isEmpty(value: unknown): boolean {
  return value === null || value === undefined || value === ''
}

/** 关联对象 / 关联对象数组 → 文本 */
function payloadText(value: unknown): string {
  if (Array.isArray(value)) {
    return value.map(item => payloadText(item)).filter(text => text !== '-').join('、') || '-'
  }
  if (value && typeof value === 'object') {
    const payload = value as { getPayloadLabel?: () => string, name?: string, code?: string }
    if (typeof payload.getPayloadLabel === 'function') {
      return payload.getPayloadLabel() || '-'
    }
    if (payload.name) {
      return payload.code ? `${payload.name}(${payload.code})` : payload.name
    }
  }
  return String(value)
}

/**
 * 取单元格显示文本。
 *
 * @param row 行数据（或任意实体实例）
 * @param config 列配置（至少要 `key`）
 * @param dictionary 字典（通常由 `getDictionary(EntityClass, key)` 得到）
 */
export function getCellText(row: unknown, config: ICellConfig, dictionary?: IWebEnumConstructor | null): string {
  const value = (row as Record<string, unknown> | null)?.[config.key]

  if (config.formatter) {
    const formatted = config.formatter(row as never, value)
    return isEmpty(formatted) ? '-' : String(formatted)
  }

  if (isEmpty(value))
    return '-'

  const enumLabel = getEnumLabel(dictionary, value)
  if (enumLabel)
    return enumLabel

  if (config.datetime) {
    const formatter = config.datetime === true ? DateTimeFormatter.FULL_DATE_TIME : config.datetime
    if (formatter instanceof DateTimeFormatter) {
      return formatter.format(value as number)
    }
  }

  const desensitize = config.desensitize ?? (config.phone ? DesensitizeType.MOBILE : undefined)
  if (desensitize instanceof DesensitizeType) {
    return desensitize.desensitize(String(value))
  }

  if (config.payload) {
    return payloadText(value)
  }

  if (config.money) {
    return formatMoney(value, config.money === true ? 2 : config.money)
  }

  if (typeof value === 'boolean')
    return value ? '是' : '否'

  if (Array.isArray(value))
    return value.length ? value.map(item => payloadText(item)).join('、') : '-'

  return String(value)
}

/** 单元格颜色：字典按值取色（数据密集场景靠颜色扫读比读文字快） */
export function getCellColor(dictionary: IWebEnumConstructor | null | undefined, value: unknown): string | undefined {
  return getEnumColor(dictionary, value)
}

/** 列对齐：金额/数量右对齐（数字列左对齐时位数对不齐，扫读困难） */
export function getColumnAlign(column: ITableColumn): 'left' | 'center' | 'right' {
  if (column.align)
    return column.align
  return column.money ? 'right' : 'left'
}
