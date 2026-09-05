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
 * - `FULL` — 完全遮蔽（返回固定星号）
 * - `PARTIAL` — 部分遮蔽（保留首尾字符，中间星号）
 * - `REPLACE` — 替换为固定表达式
 */
export type MaskType = 'FULL' | 'PARTIAL' | 'REPLACE'

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