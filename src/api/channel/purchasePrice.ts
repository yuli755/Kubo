import type { PurchasePriceEntity, PurchasePriceQuery } from './types'
import { request } from '@/api/axios'

export const purchasePriceApi = {
  getPage(params: PurchasePriceQuery): Promise<{ list: PurchasePriceEntity[], total: number }> {
    return request.post('/purchasePrice/getPage', params)
  },

  getDetail(id: number): Promise<PurchasePriceEntity> {
    return request.post('/purchasePrice/getDetail', { id })
  },

  add(data: Partial<PurchasePriceEntity>): Promise<PurchasePriceEntity> {
    return request.post('/purchasePrice/add', data)
  },

  update(data: Partial<PurchasePriceEntity>): Promise<PurchasePriceEntity> {
    return request.post('/purchasePrice/update', data)
  },

  delete(id: number): Promise<void> {
    return request.post('/purchasePrice/delete', { id })
  },
}
