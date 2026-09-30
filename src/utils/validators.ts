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

/**
 * 邮箱（与 `src/framework/validate.ts` 的 `ValidateUtil.isEmail`、后端
 * `WebhookUrlGuard.EMAIL` 同源：local part 允许 `\w`（含下划线）与 `.`/`%`/`+`/`-`）。
 *
 * 真机走查发现：原实现 `[a-z0-9]+(?:\.[a-z0-9]+)*` 拒绝下划线，而用户编辑弹窗
 * （框架 `ValidateUtil.isEmail`）与后端均接受下划线 → 下划线邮箱用户（如 WMS_OPERATOR
 * 仓储专员 `wms_1790583398729@kubo.test`）创建后无法通过登录页登录（形成死锁）。
 * 统一为宽松同源模式，`+`/`-`/`%` 与 `.` 序列（点可连续）与原框架语义一致。
 */
export function isEmail(value: string): boolean {
  return /^[\w.%+-]+@[\w.-]+\.[a-z]{2,}$/i.test(value)
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
