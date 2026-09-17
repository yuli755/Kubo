/**
 * # 框架层·数值/金额格式化
 *
 * 表格里的金额列、数量列是数据密集型页面的高频需求。原库用 `AMoney` 组件承担，
 * 这里退化成**纯函数**（表格单元格、详情页、导出都复用同一份实现，不会出现三套千分位规则）。
 */

/** 千分位（整数部分），`1234567.891` → `1,234,567.891` */
export function formatThousands(value: number | string, precision?: number): string {
  const num = typeof value === 'string' ? Number(value) : value
  if (!Number.isFinite(num))
    return '-'
  const fixed = precision === undefined ? String(num) : num.toFixed(precision)
  const [int, decimal] = fixed.split('.')
  const sign = int.startsWith('-') ? '-' : ''
  const digits = sign ? int.slice(1) : int
  const grouped = digits.replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  return decimal === undefined ? `${sign}${grouped}` : `${sign}${grouped}.${decimal}`
}

/**
 * 金额：默认两位小数 + 千分位；空值统一 `-`。
 *
 * 传入 `precision` 可覆盖小数位（如单价 4 位），传 `false` 表示不补零（原样展示）。
 *
 * ⚠️ 用 `Number(value)` 而不是字符串截取：后端金额是 `BigDecimal`，序列化后可能是
 * 字符串（`"12.30"`）也可能是数字（`12.3`），字符串截取会把 `"12.30"` 显示成 `12.3`。
 */
export function formatMoney(value: unknown, precision: number | false = 2): string {
  if (value === null || value === undefined || value === '')
    return '-'
  const num = Number(value)
  if (!Number.isFinite(num))
    return '-'
  return formatThousands(num, precision === false ? undefined : precision)
}

/**
 * 数量：整数不补小数位，小数按实际位数（`1000` → `1,000`、`12.5` → `12.5`）。
 */
export function formatQuantity(value: unknown): string {
  if (value === null || value === undefined || value === '')
    return '-'
  const num = Number(value)
  if (!Number.isFinite(num))
    return '-'
  return formatThousands(num)
}
