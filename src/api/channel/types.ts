/**
 * # Channel 模块 API 类型定义
 *
 * 与后端 airpower 框架的 @Api 注解 + 自动生成的 CRUD 端点对齐。
 */

// ──────────────────────────────────────────
// 通用 Bill 请求体
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

// ──────────────────────────────────────────
// 销售单 (Sale)
// ──────────────────────────────────────────

export interface SaleDetailEntity {
  id: number
  billId: number
  materialId: number
  materialCode: string
  materialName: string
  quantity: number
  finishQuantity: number
  price: number
  material?: { id: number, code: string, name: string }
}

export interface SaleEntity {
  id: number
  createTime: string
  updateTime: string
  isPublished: boolean
  isDeleted: boolean

  billCode: string
  status: number
  reason: string
  totalPrice: number
  customerId: number
  customerCode: string
  customerName: string
  customer?: { id: number, code: string, name: string, phone: string }
  details: SaleDetailEntity[]
}

export interface SaleQuery {
  page?: number
  pageSize?: number
  filter?: { status?: number }
}

// ──────────────────────────────────────────
// 采购单 (Purchase)
// ──────────────────────────────────────────

export interface PurchaseDetailEntity {
  id: number
  billId: number
  materialId: number
  materialCode: string
  materialName: string
  quantity: number
  finishQuantity: number
  price: number
  material?: { id: number, code: string, name: string }
}

export interface PurchaseEntity {
  id: number
  createTime: string
  updateTime: string
  isPublished: boolean
  isDeleted: boolean

  billCode: string
  status: number
  reason: string
  totalPrice: number
  totalRealPrice: number
  details: PurchaseDetailEntity[]
}

export interface PurchaseQuery {
  page?: number
  pageSize?: number
  filter?: { status?: number }
}

// ──────────────────────────────────────────
// 客户 (Customer)
// ──────────────────────────────────────────

export interface CustomerEntity {
  id: number
  createTime: string
  updateTime: string
  isPublished: boolean
  isDeleted: boolean

  code: string
  name: string
  phone: string
}

export interface CustomerQuery {
  page?: number
  pageSize?: number
  filter?: { name?: string, code?: string, phone?: string }
}

// ──────────────────────────────────────────
// 供应商 (Supplier)
// ──────────────────────────────────────────

export interface SupplierEntity {
  id: number
  createTime: string
  updateTime: string
  isPublished: boolean
  isDeleted: boolean

  code: string
  name: string
  phone: string
}

export interface SupplierQuery {
  page?: number
  pageSize?: number
  filter?: { name?: string, code?: string, phone?: string }
}

// ──────────────────────────────────────────
// 采购价 (PurchasePrice)
// ──────────────────────────────────────────

export interface PurchasePriceEntity {
  id: number
  createTime: string
  updateTime: string
  isPublished: boolean
  isDeleted: boolean

  materialId: number
  materialCode: string
  materialName: string
  supplierId: number
  supplierCode: string
  supplierName: string
  price: number
  material?: { id: number, code: string, name: string }
  supplier?: { id: number, code: string, name: string, phone: string }
}

export interface PurchasePriceQuery {
  page?: number
  pageSize?: number
}

// ──────────────────────────────────────────
// 销售价 (SalePrice)
// ──────────────────────────────────────────

export interface SalePriceEntity {
  id: number
  createTime: string
  updateTime: string
  isPublished: boolean
  isDeleted: boolean

  materialId: number
  materialCode: string
  materialName: string
  customerId: number
  customerCode: string
  customerName: string
  price: number
  material?: { id: number, code: string, name: string }
  customer?: { id: number, code: string, name: string, phone: string }
}

export interface SalePriceQuery {
  page?: number
  pageSize?: number
}
