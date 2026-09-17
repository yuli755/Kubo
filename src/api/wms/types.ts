/**
 * # WMS 模块 API 类型定义
 *
 * 与后端 airpower 框架的 @Api 注解 + 自动生成的 CRUD 端点对齐。
 * 增量式叠加，不破坏现有 @airpower/web 装饰器实体。
 */

// ──────────────────────────────────────────
// 库存 (Inventory)
// ──────────────────────────────────────────

// ──────────────────────────────────────────
// 入库单 (Input)
// ──────────────────────────────────────────

interface InputDetailEntity {
  id: number
  createTime: string
  updateTime: string
  isPublished: boolean
  isDeleted: boolean

  materialCode: string
  materialName: string
  quantity: number
  finishQuantity: number
  storageName: string
  material?: { id: number, code: string, name: string }
  storage?: { id: number, name: string, code: string }
  materialId: number
  storageId: number
}

export interface InputEntity {
  id: number
  createTime: string
  updateTime: string
  isPublished: boolean
  isDeleted: boolean

  billCode: string
  type: number
  status: number
  rejectReason: string
  storage?: { id: number, name: string, code: string }
  details: InputDetailEntity[]
  purchase?: { id: number, billCode: string }
  move?: { id: number, billCode: string }
  order?: { id: number, billCode: string }
}

export interface InputQuery {
  page?: number
  pageSize?: number
  filter?: {
    billCode?: string
    type?: number
    status?: number
  }
}

// ──────────────────────────────────────────
// 出库单 (Output)
// ──────────────────────────────────────────

interface OutputDetailEntity {
  id: number
  createTime: string
  updateTime: string
  isPublished: boolean
  isDeleted: boolean

  materialCode: string
  materialName: string
  quantity: number
  finishQuantity: number
  storageName: string
  material?: { id: number, code: string, name: string }
  inventory?: { id: number, storage: { id: number, name: string, code: string } }
  materialId: number
  inventoryId: number
}

export interface OutputEntity {
  id: number
  createTime: string
  updateTime: string
  isPublished: boolean
  isDeleted: boolean

  billCode: string
  type: number
  status: number
  rejectReason: string
  details: OutputDetailEntity[]
  sale?: { id: number, billCode: string }
  move?: { id: number, billCode: string }
  picking?: { id: number, billCode: string }
}

export interface OutputQuery {
  page?: number
  pageSize?: number
  filter?: {
    billCode?: string
    type?: number
    status?: number
  }
}

// ──────────────────────────────────────────
// 移库单 (Move)
// ──────────────────────────────────────────

interface MoveDetailEntity {
  id: number
  createTime: string
  updateTime: string
  isPublished: boolean
  isDeleted: boolean

  materialCode: string
  materialName: string
  quantity: number
  finishQuantity: number
  storageName: string
  inventory?: { id: number, material: { code: string, name: string }, storage: { id: number, name: string, code: string } }
}

export interface MoveEntity {
  id: number
  createTime: string
  updateTime: string
  isPublished: boolean
  isDeleted: boolean

  billCode: string
  status: number
  rejectReason: string
  storageName: string
  storage?: { id: number, name: string, code: string }
  details: MoveDetailEntity[]
}

export interface MoveQuery {
  page?: number
  pageSize?: number
  filter?: {
    billCode?: string
    status?: number
  }
}

// ──────────────────────────────────────────
// 通用单据操作
// ──────────────────────────────────────────

export interface BillAuditRequest {
  id: number
}

export interface BillRejectRequest {
  id: number
  rejectReason: string
}

export interface BillFinishRequest {
  id: number
}
