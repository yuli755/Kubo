/**
 * # 框架层·数值/金额格式化
 *
 * 表格里的金额列、数量列是数据密集型页面的高频需求。原库用 `AMoney` 组件承担，
 * 这里退化成**纯函数**（表格单元格、详情页、导出都复用同一份实现，不会出现三套千分位规则）。
 *
 * ## R40（§15.8 → §16）：分组/舍入委托 `Intl.NumberFormat`，不再手拼正则 + toFixed
 * - **舍入与后端对齐（这条是主因，真机实测 2026-10-02）**：`2.675` 的二进制真身略小于
 *   面值，`toFixed` 忠实渲染真身 → `(2.675).toFixed(2) === '2.67'`；后端
 *   `BigDecimal("2.675").setScale(2, HALF_UP) === 2.68`。`Intl.NumberFormat` 按规范
 *   对**十进制字符串**舍入（ECMA-402 number-to-string，halfExpand 远离零）→ `2.68`，
 *   与后端一致。像 `1.005 → 1.00/1.01` 这类「写起来两位、二进制存不下」的金额，正是
 *   前后端「各自都没错但对不上」的高发区。（教科书常引的 `-0.125` 平局偏差在 V8 上
 *   并不存在——它给 `-0.13`；真正分歧只在不可表示小数，测试按实测口径钉住。）
 * - **指数形态**：`String(1e21)` → `1e+21`、`String(1e-7)` → `1e-7`，对它们正则分组
 *   无能为力；Intl 给出完整十进制（`1,000,000,000,000,000,000,000` / `0.0000001`）。
 * - **区域固定 `en-US`**：`,` 分组 + `.` 小数点，与旧实现逐字节一致，且不随浏览器
 *   locale 漂移（de-DE 会把小数点显示成 `,`）。表格数字口径是产品决策，不跟系统语言走。
 */

/** 分组器缓存：`Intl.NumberFormat` 构造开销大，按 (min,max) 小数位复用 */
const formatters = new Map<string, Intl.NumberFormat>()

function formatter(minFraction: number, maxFraction: number): Intl.NumberFormat {
  const key = `${minFraction}/${maxFraction}`
  let fmt = formatters.get(key)
  if (!fmt) {
    fmt = new Intl.NumberFormat('en-US', {
      minimumFractionDigits: minFraction,
      maximumFractionDigits: maxFraction,
      useGrouping: true,
    })
    formatters.set(key, fmt)
  }
  return fmt
}

/**
 * 无显式 precision 时从数值本身推小数位数，保证 Intl 不截掉有效位：
 * `String()` 的指数形态要折算 —— `1.5e-7` 真身有 8 位小数，`1e+21` 是纯整数（0 位）。
 * 上限 20：`maximumFractionDigits` 在旧实现（`toFixed`）与本实现的安全公共域。
 */
function decimalDigitsOf(num: number): number {
  const str = String(num)
  const sci = /^-?\d+(?:\.(\d+))?e([+-]\d+)$/i.exec(str)
  if (sci) {
    const mantissaFrac = sci[1]?.length ?? 0
    return Math.min(20, Math.max(0, mantissaFrac - Number(sci[2])))
  }
  const dot = str.indexOf('.')
  return dot === -1 ? 0 : Math.min(20, str.length - dot - 1)
}

/** 千分位（整数部分），`1234567.891` → `1,234,567.891` */
export function formatThousands(value: number | string, precision?: number): string {
  const num = typeof value === 'string' ? Number(value) : value
  if (!Number.isFinite(num))
    return '-'
  // -0 归一：Intl 会忠实渲染成 "-0"，而旧实现 String(-0) 是 "0"——数量列出现 "-0" 只会引来工单
  const safe = Object.is(num, -0) ? 0 : num
  if (precision === undefined) {
    return formatter(0, decimalDigitsOf(safe)).format(safe)
  }
  return formatter(precision, precision).format(safe)
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
