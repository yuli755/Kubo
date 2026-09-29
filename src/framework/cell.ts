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

/**
 * 关联列没标 `payload` 时，按这几个键找一个人能读的标签。
 *
 * 顺序有讲究：先业务标识（resourceKey/label/title），再人称字段。
 * 找不到就退到 `#id`，再退到 `-`；**任何情况下都不返回 `[object Object]`** ——
 * 那串字面量既不可读也不可搜，而本函数被表格 / 详情 / 导出三处共用，
 * 一处吐 `[object Object]` 就是三个地方一起吐。
 */
const FALLBACK_LABEL_KEYS = ['resourceKey', 'label', 'title', 'nickname', 'realName'] as const

/** 对象值 → 标签文本；实在取不到就返回空串，由调用方决定兜底 */
function labelOfObject(value: object): string {
  const payload = value as {
    getPayloadLabel?: () => string
    name?: string
    code?: string
    id?: number | string | null
  } & Record<string, unknown>
  if (typeof payload.getPayloadLabel === 'function') {
    return payload.getPayloadLabel() || ''
  }
  if (payload.name) {
    return payload.code ? `${payload.name}(${payload.code})` : payload.name
  }
  for (const key of FALLBACK_LABEL_KEYS) {
    const candidate = payload[key]
    if (typeof candidate === 'string' && candidate) {
      return candidate
    }
  }
  if (payload.id !== null && payload.id !== undefined) {
    return `#${payload.id}`
  }
  return ''
}

/** 关联对象 / 关联对象数组 → 文本 */
function payloadText(value: unknown): string {
  if (Array.isArray(value)) {
    return value.map(item => payloadText(item)).filter(text => text !== '-').join('、') || '-'
  }
  if (value && typeof value === 'object') {
    return labelOfObject(value) || '-'
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

  if (value instanceof Date) {
    // 旧行为是 String(date)（可读），保持不变：别让它掉进下面的对象兜底变成 '-'
    return String(value)
  }

  if (typeof value === 'object') {
    // 关联列忘了标 `payload` 时的兜底。真机实例：列级脱敏页的 `resource` 列渲染成
    // `[object Object]`，而它本该显示 resourceKey。标 `payload: true` 仍是正解，
    // 但漏标不该退化成不可读的字符串。
    return payloadText(value)
  }

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
