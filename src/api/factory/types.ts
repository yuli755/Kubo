/**
 * # Factory 模块 API 类型定义
 *
 * 与后端 airpower 框架的 @Api 注解 + 自动生成的 CRUD 端点对齐。
 * 仓储/生产单元均为树形实体（ITree），使用 getList 而非 getPage 获取数据。
 */

// ──────────────────────────────────────────
// 仓库 (Storage) — 树形实体
// ──────────────────────────────────────────

export interface StorageEntity {
  id: number
  createTime: string
  updateTime: string
  isPublished: boolean
  isDeleted: boolean

  code: string
  name: string
  parentId: number
  children: StorageEntity[]
  parent?: { id: number, name: string, code: string }
}

// ──────────────────────────────────────────
// 生产单元 (Structure) — 树形实体
// ──────────────────────────────────────────

export interface StructureEntity {
  id: number
  createTime: string
  updateTime: string
  isPublished: boolean
  isDeleted: boolean

  code: string
  name: string
  type: number
  parentId: number
  children: StructureEntity[]
  parent?: { id: number, name: string, code: string }
  operationList?: Array<{ id: number, code: string, name: string }>
  departmentList?: Array<{ id: number, name: string, code: string }>
}
