import type { BillAuditRequest, BillFinishRequest, BillRejectRequest, SaleEntity, SaleQuery } from './types'
import { request } from '@/api/axios'

export const saleApi = {
  getPage(params: SaleQuery): Promise<{ list: SaleEntity[], total: number }> {
    return request.post('/sale/getPage', params)
  },

  getDetail(id: number): Promise<SaleEntity> {
    return request.post('/sale/getDetail', { id })
  },

  add(data: Partial<SaleEntity>): Promise<SaleEntity> {
    return request.post('/sale/add', data)
  },

  update(data: Partial<SaleEntity>): Promise<SaleEntity> {
    return request.post('/sale/update', data)
  },

  audit(data: BillAuditRequest): Promise<void> {
    return request.post('/sale/audit', data)
  },

  reject(data: BillRejectRequest): Promise<void> {
    return request.post('/sale/reject', data)
  },

  setBillDetailsAllFinished(data: BillFinishRequest): Promise<void> {
    return request.post('/sale/setBillDetailsAllFinished', data)
  },
}
