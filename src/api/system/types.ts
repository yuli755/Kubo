/**
 * # System 模块 API 类型定义
 */

import type { MaskType } from '@kubo/shared'

// ──────────────────────────────────────────
// 菜单 (Menu) — 树形实体
// ──────────────────────────────────────────

export interface MenuEntity {
  id: number
  createTime: string
  updateTime: string
  isPublished: boolean
  isDeleted: boolean

  name: string
  path: string
  icon: string
  component: string
  orderNo: number
  parentId: number
  children?: MenuEntity[]
  parent?: MenuEntity
  permission?: string
}

export interface MenuQuery {
  page?: number
  pageSize?: number
  filter?: { name?: string, path?: string, component?: string }
}

// ──────────────────────────────────────────
// 权限 (Permission) — 树形实体
// ──────────────────────────────────────────

export interface PermissionEntity {
  id: number
  createTime: string
  updateTime: string
  isPublished: boolean
  isDeleted: boolean

  name: string
  identity: string
  type: number
  isSystem: boolean
  parentId: number
  children?: PermissionEntity[]
  parent?: PermissionEntity
}

export interface PermissionQuery {
  page?: number
  pageSize?: number
  filter?: { name?: string, identity?: string, type?: number }
}

// ──────────────────────────────────────────
// 计量单位 (Unit)
// ──────────────────────────────────────────

export interface UnitEntity {
  id: number
  createTime: string
  updateTime: string
  isPublished: boolean
  isDeleted: boolean

  name: string
  code: string
}

export interface UnitQuery {
  page?: number
  pageSize?: number
  filter?: { name?: string, code?: string }
}

// ──────────────────────────────────────────
// 参数配置 (Config)
// ──────────────────────────────────────────

export interface ConfigEntity {
  id: number
  createTime: string
  updateTime: string
  isPublished: boolean
  isDeleted: boolean

  name: string
  description: string
  flag: string
  type: number
  isSystem: boolean
  config: string
}

export interface ConfigQuery {
  page?: number
  pageSize?: number
  filter?: { name?: string, flag?: string, type?: number }
}

// ──────────────────────────────────────────
// 编码规则 (CodeRule)
// ──────────────────────────────────────────

export interface CodeRuleEntity {
  id: number
  createTime: string
  updateTime: string
  isPublished: boolean
  isDeleted: boolean

  ruleField: number
  prefix: string
  snLength: number
  template: string
  snType: number
  nextCode: string
  currentSn: number
}

export interface CodeRuleQuery {
  page?: number
  pageSize?: number
  filter?: { template?: string, snType?: number }
}

export interface CodeRuleField {
  defaultPrefix: string
  label: string
  key: number
}

export interface CodeRuleParam {
  label: string
  value: number
  demo: string
  desc: string
}
// ──────────────────────────────────────────
// 数据字段权限策略（DataFieldPolicy · 列级脱敏）
// ──────────────────────────────────────────

/**
 * 后端脱敏类型与策略类型由 @kubo/shared 承载，这里仅做再导出，
 * 保持 `@/api/system/types` 的历史导入路径可用。
 */
export type { MaskPolicy, MaskType } from '@kubo/shared'

/**
 * ### 列级脱敏字段策略实体
 *
 * 与后端 `cn.hamm.spms.module.system.datapermission.entity.DataFieldPolicyEntity` 对齐。
 * `my-mask` 端点在「当前用户无需脱敏」时返回 `null`。
 */
export interface DataFieldPolicyEntity {
  id: number
  /** 关联的数据资源 ID */
  resourceId: number
  /** 字段名（如 price / telephone） */
  fieldName: string
  /** 角色编码；`default` 为通配兜底 */
  roleCode: string
  /** 动作名（QUERY / EXPORT 等） */
  actionName: string
  /** 脱敏类型 */
  maskType: MaskType
  /** REPLACE 类型的替换表达式 */
  maskExpression?: string
  /** 是否允许明文 */
  allowPlaintext: boolean
  /** 策略优先级，越大越优先 */
  priority: number
  /** 是否启用 */
  enabled: boolean
}

export interface DataFieldPolicyQuery {
  page?: number
  pageSize?: number
  filter?: { fieldName?: string, roleCode?: string, actionName?: string, enabled?: boolean }
}

/**
 * 一条"导出明文授权"缺口：某角色在某字段上被遮蔽，却没有配套的 EXPORT 明文授权。
 *
 * 结论由后端算（`FieldPolicyGapAnalyzer`），前端只渲染：判据有两处实现就已经是隐患，
 * 三处必然漂移，而漂移的表现是"面板说没问题、用户点导出被拒"。
 *
 * 两个资源定位字段都是后端带的：策略编辑器手上只有 `resourceId`（表单就提交这个字段），
 * 若响应只给 resourceKey，前端就得再查一次"id → key"，多一次请求、多一处会写歪的映射。
 */
export interface ExportGapItem {
  /** 数据资源 ID，与 `DataFieldPolicyEntity.resourceId` 同域，可直接比对 */
  resourceId: number
  /** 数据资源标识（给人看的） */
  resourceKey: string
  /** 被遮蔽的字段名 */
  fieldName: string
  /** 生效角色；`default` 表示对所有角色兜底 */
  roleCode: string
  /** 人话说明（后端给，便于与 guard 的拒因保持同一措辞） */
  reason: string
}
