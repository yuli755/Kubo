/**
 * # MES 模块 API 类型定义
 *
 * 与后端 airpower 框架的 @Api 注解 + 自动生成的 CRUD 端点对齐。
 * 增量式叠加，不破坏现有 @airpower/web 装饰器实体。
 */

// ──────────────────────────────────────────
// 生产订单 (Order) — Bill 类型
// ──────────────────────────────────────────

export interface OrderDetailEntity {
  id: number
  createTime: string
  updateTime: string
  isPublished: boolean
  isDeleted: boolean

  billId: number
  materialId: number
  materialCode: string
  materialName: string
  quantity: number
  finishQuantity: number
  ngQuantity: number
  material?: { id: number, code: string, name: string }
}

export interface OrderEntity {
  id: number
  createTime: string
  updateTime: string
  isPublished: boolean
  isDeleted: boolean

  billCode: string
  status: number
  rejectReason: string
  materialCode: string
  materialName: string
  quantity: number
  finishQuantity: number
  ngQuantity: number
  planBillCode: string
  type: number
  startTime: number
  deliverTime: number
  finishTime: number
  details: OrderDetailEntity[]
  material?: { id: number, code: string, name: string }
  plan?: { id: number, billCode: string }
  routing?: { id: number, name: string, code: string }
  customer?: { id: number, name: string, code: string }
}

export interface OrderQuery {
  page?: number
  pageSize?: number
  filter?: {
    billCode?: string
    type?: number
    status?: number
  }
}

/** 订单报工请求（addOrderDetail） */
export interface OrderReportRequest {
  id: number
  billId: number
  materialId: number
  quantity: number
  ngQuantity: number
  useTime: number
}

/** 订单暂停请求（pause） */
export interface OrderPauseRequest {
  id: number
}

// ──────────────────────────────────────────
// 生产计划 (Plan) — Bill 类型
// ──────────────────────────────────────────

export interface PlanDetailEntity {
  id: number
  createTime: string
  updateTime: string
  isPublished: boolean
  isDeleted: boolean

  billId: number
  materialId: number
  materialCode: string
  materialName: string
  quantity: number
  finishQuantity: number
  material?: { id: number, code: string, name: string }
}

export interface PlanEntity {
  id: number
  createTime: string
  updateTime: string
  isPublished: boolean
  isDeleted: boolean

  billCode: string
  status: number
  rejectReason: string
  customerCode: string
  customerName: string
  customerId: number
  type: number
  startTime: number
  deliverTime: number
  finishTime: number
  details: PlanDetailEntity[]
  customer?: { id: number, name: string, code: string }
}

export interface PlanQuery {
  page?: number
  pageSize?: number
  filter?: {
    billCode?: string
    type?: number
    status?: number
  }
}

// ──────────────────────────────────────────
// 领料单 (Picking) — Bill 类型
// ──────────────────────────────────────────

export interface PickingDetailEntity {
  id: number
  createTime: string
  updateTime: string
  isPublished: boolean
  isDeleted: boolean

  billId: number
  materialId: number
  materialCode: string
  materialName: string
  quantity: number
  finishQuantity: number
  material?: { id: number, code: string, name: string }
}

export interface PickingEntity {
  id: number
  createTime: string
  updateTime: string
  isPublished: boolean
  isDeleted: boolean

  billCode: string
  status: number
  rejectReason: string
  details: PickingDetailEntity[]
  structure?: { id: number, name: string, code: string }
  order?: { id: number, billCode: string }
}

export interface PickingQuery {
  page?: number
  pageSize?: number
  filter?: {
    billCode?: string
    status?: number
  }
}

// ──────────────────────────────────────────
// 生产配方 (Bom) — 非 Bill 类型
// ──────────────────────────────────────────

export interface BomDetailEntity {
  id: number
  createTime: string
  updateTime: string
  isPublished: boolean
  isDeleted: boolean

  bomId: number
  materialId: number
  materialCode: string
  materialName: string
  quantity: number
  finishQuantity: number
  material?: { id: number, code: string, name: string }
}

export interface BomEntity {
  id: number
  createTime: string
  updateTime: string
  isPublished: boolean
  isDeleted: boolean

  code: string
  name: string
  type: number
  details: BomDetailEntity[]
}

export interface BomQuery {
  page?: number
  pageSize?: number
  filter?: {
    code?: string
    name?: string
    type?: number
  }
}

// ──────────────────────────────────────────
// 工序 (Operation) — 非 Bill 类型
// ──────────────────────────────────────────

export interface OperationEntity {
  id: number
  createTime: string
  updateTime: string
  isPublished: boolean
  isDeleted: boolean

  code: string
  name: string
}

export interface OperationQuery {
  page?: number
  pageSize?: number
  filter?: {
    code?: string
    name?: string
  }
}

// ──────────────────────────────────────────
// 生产工艺 (Routing) — 非 Bill 类型
// ──────────────────────────────────────────

export interface RoutingOperationDetailEntity {
  id: number
  createTime: string
  updateTime: string
  isPublished: boolean
  isDeleted: boolean

  routingId: number
  bom?: { id: number, code: string, name: string }
  operation?: { id: number, code: string, name: string }
  sortNo: number
  isAutoNext: boolean
}

export interface RoutingEntity {
  id: number
  createTime: string
  updateTime: string
  isPublished: boolean
  isDeleted: boolean

  code: string
  name: string
  materialCode: string
  materialName: string
  bomCode: string
  bomName: string
  isRoutingBom: boolean
  details: RoutingOperationDetailEntity[]
  material?: { id: number, code: string, name: string }
  bom?: { id: number, code: string, name: string }
}

export interface RoutingQuery {
  page?: number
  pageSize?: number
  filter?: {
    code?: string
    name?: string
    isRoutingBom?: boolean
  }
}

// ──────────────────────────────────────────
// 通用单据操作（与 WMS 模块共享）
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
