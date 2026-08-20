/**
 * # System 模块 API 类型定义
 */

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