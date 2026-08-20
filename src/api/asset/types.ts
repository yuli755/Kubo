/**
 * # Asset 模块 API 类型定义
 */

// ──────────────────────────────────────────
// 设备 (Device)
// ──────────────────────────────────────────

export interface DeviceEntity {
  id: number
  createTime: string
  updateTime: string
  isPublished: boolean
  isDeleted: boolean

  name: string
  code: string
  uuid: string
  isReporting: boolean
  partCount: string
  status: number
  alarm: number
  rate: number
  parameters?: { id: number, name: string, code: string }[]
}

export interface DeviceQuery {
  page?: number
  pageSize?: number
  filter?: { name?: string, code?: string, uuid?: string, status?: number }
}

export interface CollectionEntity {
  id: number
  createTime: string
  updateTime: string
  deviceId: number
  payload?: Record<string, unknown>
  [key: string]: unknown
}

// ──────────────────────────────────────────
// 物料 (Material)
// ──────────────────────────────────────────

export interface MaterialEntity {
  id: number
  createTime: string
  updateTime: string
  isPublished: boolean
  isDeleted: boolean

  name: string
  code: string
  materialType: number
  useType: number
  spc: string
  purchasePrice: number
  salePrice: number
  unit?: { id: number, name: string }
  unitName?: string
}

export interface MaterialQuery {
  page?: number
  pageSize?: number
  filter?: { name?: string, code?: string, materialType?: number, useType?: number }
}

// ──────────────────────────────────────────
// 合同 (Contract)
// ──────────────────────────────────────────

export interface ContractEntity {
  id: number
  createTime: string
  updateTime: string
  isPublished: boolean
  isDeleted: boolean

  code: string
  type: number
  name: string
  content: string
  startTime: number
  endTime: number
  status: number
  participantList?: { id: number, name: string, role: number, type: number }[]
  documentList?: { id: number, name: string, url: string }[]
}

export interface ContractQuery {
  page?: number
  pageSize?: number
  filter?: { name?: string, code?: string, type?: number, status?: number }
}