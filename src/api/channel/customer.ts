import type { CustomerEntity, CustomerQuery } from './types'
import { request } from '@/api/axios'

export const customerApi = {
  getPage(params: CustomerQuery): Promise<{ list: CustomerEntity[], total: number }> {
    return request.post('/customer/getPage', params)
  },

  getDetail(id: number): Promise<CustomerEntity> {
    return request.post('/customer/getDetail', { id })
  },

  add(data: Partial<CustomerEntity>): Promise<CustomerEntity> {
    return request.post('/customer/add', data)
  },

  update(data: Partial<CustomerEntity>): Promise<CustomerEntity> {
    return request.post('/customer/update', data)
  },

  delete(id: number): Promise<void> {
    return request.post('/customer/delete', { id })
  },
}
