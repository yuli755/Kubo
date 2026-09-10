/**
 * # 表单校验工具（「去 airpower 化入口」配套）
 *
 * 复刻 `@airpower/web` 的 `ValidateUtil` 三个方法，供登录页等首屏视图使用，
 * 避免首屏为这几个正则引入整个 airpower 单体。
 *
 * 与 airpower 原实现的差异（均不影响判定语义，仅消除 lint 告警）：
 *   - 捕获组 `(...)` 改为非捕获组 `(?:...)`（eslint `regexp/no-unused-capturing-group`）；
 *   - `isNaturalNumber` 的 `(.)`（airpower 原样，未转义点号）修正为 `\.` 小数点，
 *     既符合函数「自然数/小数」语义，也消除 `\d+` 与 `.` 重叠导致的
 *     `regexp/no-super-linear-backtracking` 回溯告警。
 */

/** 邮箱（airpower `ValidateUtil.isEmail`，语义一致） */
export function isEmail(value: string): boolean {
  return /^[a-z0-9]+(?:\.[a-z0-9]+)*@[a-z0-9]+(?:\.[a-z0-9]+)+$/i.test(value)
}

/** 手机号（airpower `ValidateUtil.isMobilePhone`：`+` 国家码整体可选，语义一致） */
export function isMobilePhone(value: string): boolean {
  // airpower 原版 /^(\+(\d{1,4}))?1[3-9](\d{9})$/：`+` 与 1-4 位国家码是「整体可选」，
  // 即裸手机号（无 +）也匹配。捕获组改非捕获组，语义不变。
  return /^(?:\+\d{1,4})?1[3-9]\d{9}$/.test(value)
}

/** 自然数/小数（airpower `ValidateUtil.isNaturalNumber`，`\.` 修正点号笔误） */
export function isNaturalNumber(value: string): boolean {
  return /^\d+(?:\.\d+)?$/.test(value)
}
