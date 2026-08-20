import type { SupplierEntity, SupplierQuery } from './types'
import { request } from '@/api/axios'

export const supplierApi = {
  getPage(params: SupplierQuery): Promise<{ list: SupplierEntity[], total: number }> {
    return request.post('/supplier/getPage', params)
  },

  getDetail(id: number): Promise<SupplierEntity> {
    return request.post('/supplier/getDetail', { id })
  },

  add(data: Partial<SupplierEntity>): Promise<SupplierEntity> {
    return request.post('/supplier/add', data)
  },

  update(data: Partial<SupplierEntity>): Promise<SupplierEntity> {
    return request.post('/supplier/update', data)
  },

  delete(id: number): Promise<void> {
    return request.post('/supplier/delete', { id })
  },
}