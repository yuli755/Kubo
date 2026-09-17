/**
 * # 框架层·时间工具
 *
 * 后端返回的时间统一是**毫秒时间戳（数字）或 ISO 字符串**，展示层需要按列格式化：
 * 表格列 `datetime: true` / 表单 `DateTimeFormatter.FULL_DATE` 都走这里。
 */

export class DateTimeUtil {
  static readonly SECOND_PER_MINUTE = 60
  static readonly SECOND_PER_HOUR = 60 * 60
  static readonly MILLISECONDS_PER_SECOND = 1000
  static readonly SECOND_PER_DAY = 24 * 60 * 60
  static readonly DAY_PER_WEEK = 7
  static readonly DAY_PER_MONTH = 30
  static readonly DAY_PER_YEAR = 365

  /** 当前毫秒时间戳 */
  static getMilliTimeStamps(date?: Date | string | number): number {
    if (date === undefined) {
      return Date.now()
    }
    return new Date(date).getTime()
  }

  /** 当前秒时间戳 */
  static getUnixTimeStamps(date?: Date | string | number): number {
    return Math.floor(DateTimeUtil.getMilliTimeStamps(date) / DateTimeUtil.MILLISECONDS_PER_SECOND)
  }

  /**
   * 按模式串格式化日期（原库写法：`formatFromDate(date, 'YYYY-MM-DD')`）。
   *
   * 支持 `YYYY / YY / MM / DD / HH / mm / ss`；编码规则页把它当**模板占位符**
   * 用（`yyyy`、`mm`、`dd`、`hh` 会被替换成当前时间），所以 `yyyy` 这种
   * 「全小写年份」也要认 —— 那是业务模板的写法，不是笔误。
   */
  static formatFromDate(date: Date | string | number, pattern: string): string {
    const target = date instanceof Date ? date : new Date(date)
    if (Number.isNaN(target.getTime())) {
      return ''
    }
    const pad = (value: number) => String(value).padStart(2, '0')
    const tokens: Record<string, string> = {
      YYYY: String(target.getFullYear()),
      yyyy: String(target.getFullYear()),
      YY: String(target.getFullYear()).slice(-2),
      yy: String(target.getFullYear()).slice(-2),
      MM: pad(target.getMonth() + 1),
      mm: pad(target.getMonth() + 1),
      DD: pad(target.getDate()),
      dd: pad(target.getDate()),
      HH: pad(target.getHours()),
      hh: pad(target.getHours()),
      ss: pad(target.getSeconds()),
    }
    return pattern.replace(/YYYY|yyyy|YY|yy|MM|mm|DD|dd|HH|hh|ss/g, matched => tokens[matched] ?? matched)
  }

  /** 相对时间：刚刚 / 3 分钟前 / 2 天前（列表里比绝对时间更好读） */
  static getFriendlyDateTime(date: Date | string | number): string {
    const target = new Date(date).getTime()
    const diff = Date.now() - target
    if (Number.isNaN(target)) {
      return '-'
    }
    if (diff < DateTimeUtil.MILLISECONDS_PER_SECOND * DateTimeUtil.SECOND_PER_MINUTE) {
      return '刚刚'
    }
    if (diff < DateTimeUtil.MILLISECONDS_PER_SECOND * DateTimeUtil.SECOND_PER_HOUR) {
      return `${Math.floor(diff / (DateTimeUtil.MILLISECONDS_PER_SECOND * DateTimeUtil.SECOND_PER_MINUTE))} 分钟前`
    }
    if (diff < DateTimeUtil.MILLISECONDS_PER_SECOND * DateTimeUtil.SECOND_PER_DAY) {
      return `${Math.floor(diff / (DateTimeUtil.MILLISECONDS_PER_SECOND * DateTimeUtil.SECOND_PER_HOUR))} 小时前`
    }
    if (diff < DateTimeUtil.MILLISECONDS_PER_SECOND * DateTimeUtil.SECOND_PER_DAY * DateTimeUtil.DAY_PER_MONTH) {
      return `${Math.floor(diff / (DateTimeUtil.MILLISECONDS_PER_SECOND * DateTimeUtil.SECOND_PER_DAY))} 天前`
    }
    return DateTimeFormatter.FULL_DATE.formatMilliSecond(target)
  }
}

/** 把任意时间输入归一成 Date（无效输入返回 null） */
function toDate(value: Date | string | number | null | undefined): Date | null {
  if (value === null || value === undefined || value === '') {
    return null
  }
  const date = value instanceof Date ? value : new Date(typeof value === 'string' && /^\d+$/.test(value) ? Number(value) : value)
  return Number.isNaN(date.getTime()) ? null : date
}

function pad(value: number): string {
  return value.toString().padStart(2, '0')
}

/**
 * # 时间格式化器
 *
 * 用法与原库一致：`DateTimeFormatter.FULL_DATE_TIME.formatMilliSecond(row.createTime)`，
 * 也可以直接当 dayjs 风格的格式串使用（`datetime: DateTimeFormatter.FULL_DATE`）。
 */
export class DateTimeFormatter {
  static readonly FULL_DATE_TIME = new DateTimeFormatter('YYYY-MM-DD HH:mm:ss')
  static readonly SHORT_DATE_TIME = new DateTimeFormatter('YYYY-MM-DD HH:mm')
  static readonly FULL_DATE = new DateTimeFormatter('YYYY-MM-DD')
  static readonly FULL_TIME = new DateTimeFormatter('HH:mm:ss')
  static readonly YEAR = new DateTimeFormatter('YYYY')
  static readonly MONTH = new DateTimeFormatter('YYYY-MM')
  static readonly DAY = new DateTimeFormatter('YYYY-MM-DD')
  static readonly HOUR = new DateTimeFormatter('HH')
  static readonly MINUTE = new DateTimeFormatter('YYYY-MM-DD HH:mm')
  static readonly SECOND = new DateTimeFormatter('YYYY-MM-DD HH:mm:ss')
  /** 原样输出毫秒时间戳（提交给后端时用） */
  static readonly TIMESTAMP = new DateTimeFormatter('timestamp')

  private readonly key: string

  constructor(key: string) {
    this.key = key
  }

  /** 格式串（也是枚举 key） */
  toString(): string {
    return this.key
  }

  /** 毫秒时间戳 → 文本 */
  formatMilliSecond(milliSecond: number): string {
    const date = toDate(milliSecond)
    return date ? this.formatDate(date) : '-'
  }

  /** 秒时间戳 → 文本 */
  formatSecond(second: number): string {
    return this.formatMilliSecond(second * DateTimeUtil.MILLISECONDS_PER_SECOND)
  }

  /** Date/字符串/数字 → 文本 */
  format(date?: Date | string | number | null): string {
    const target = toDate(date)
    return target ? this.formatDate(target) : '-'
  }

  /** Date → 文本 */
  formatDate(date: Date): string {
    if (this.key === 'timestamp') {
      return String(date.getTime())
    }
    return this.key
      .replace('YYYY', String(date.getFullYear()))
      .replace('MM', pad(date.getMonth() + 1))
      .replace('DD', pad(date.getDate()))
      .replace('HH', pad(date.getHours()))
      .replace('mm', pad(date.getMinutes()))
      .replace('ss', pad(date.getSeconds()))
  }
}
