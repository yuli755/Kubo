import type { BillAuditRequest, BillFinishRequest, BillRejectRequest, PurchaseEntity, PurchaseQuery } from './types'
import { request } from '@/api/axios'

export const purchaseApi = {
  getPage(params: PurchaseQuery): Promise<{ list: PurchaseEntity[], total: number }> {
    return request.post('/purchase/getPage', params)
  },

  getDetail(id: number): Promise<PurchaseEntity> {
    return request.post('/purchase/getDetail', { id })
  },

  add(data: Partial<PurchaseEntity>): Promise<PurchaseEntity> {
    return request.post('/purchase/add', data)
  },

  update(data: Partial<PurchaseEntity>): Promise<PurchaseEntity> {
    return request.post('/purchase/update', data)
  },

  audit(data: BillAuditRequest): Promise<void> {
    return request.post('/purchase/audit', data)
  },

  reject(data: BillRejectRequest): Promise<void> {
    return request.post('/purchase/reject', data)
  },

  setBillDetailsAllFinished(data: BillFinishRequest): Promise<void> {
    return request.post('/purchase/setBillDetailsAllFinished', data)
  },
}