/**
 * 列级脱敏（字段权限）类型定义
 *
 * 与后端 {@code data_field_policy} 表及
 * {@code GET /dataFieldPolicy/my-mask} 端点返回的 JSON 对齐。
 */

/**
 * ### 后端脱敏类型
 *
 * 与 {@code DataFieldPolicyEntity.maskType} 的取值一致：
 * - `FULL` — 完全遮蔽（返回固定星号 / 表达式）
 * - `PARTIAL` — 部分遮蔽（保留首尾字符，中间星号）
 * - `REPLACE` — 替换为固定表达式
 * - `LAST4` — 只保留末四位（长度不足整体遮蔽）
 * - `HASH` — 不可逆摘要（`h:` + SHA-256 前 12 位十六进制），同值同码可比对
 *
 * **必须与后端 `ColumnMaskingService#applyMask` 的 case 分支保持一致**：
 * `src/config/__tests__/maskContract.spec.ts` 会从 Java 源码里把 case 标签抓出来
 * 与本 union 比对，后端新增类型而前端未跟随时 CI 直接失败
 * （前端漏一个分支的后果是它落进"未知类型"，被按最严格的 `****` 处理 —— 过度遮蔽，
 * 用户看到满屏星号却查不出原因）。
 */
export type MaskType = 'FULL' | 'PARTIAL' | 'REPLACE' | 'LAST4' | 'HASH'

/**
 * ### 列级脱敏字段策略
 *
 * 对应后端 {@code DataFieldPolicyEntity} 的 JSON 表示。
 * `my-mask` 端点未命中策略时返回 `null`，表示该列对当前用户无需脱敏。
 */
export interface MaskPolicy {
  /** 脱敏类型 */
  maskType: MaskType
  /** REPLACE 类型使用的替换表达式 */
  maskExpression?: string
  /** 是否允许明文（true 时不脱敏） */
  allowPlaintext?: boolean
  /** 策略优先级（数字越大越优先） */
  priority?: number
  /** 是否启用 */
  enabled?: boolean
  /** 字段名 */
  fieldName?: string
  /** 角色编码 */
  roleCode?: string
  /** 动作名（如 QUERY） */
  actionName?: string
}
