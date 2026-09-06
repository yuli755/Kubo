import type { SalePriceEntity, SalePriceQuery } from './types'
import { request } from '@/api/axios'

export const salePriceApi = {
  getPage(params: SalePriceQuery): Promise<{ list: SalePriceEntity[], total: number }> {
    return request.post('/salePrice/getPage', params)
  },

  getDetail(id: number): Promise<SalePriceEntity> {
    return request.post('/salePrice/getDetail', { id })
  },

  add(data: Partial<SalePriceEntity>): Promise<SalePriceEntity> {
    return request.post('/salePrice/add', data)
  },

  update(data: Partial<SalePriceEntity>): Promise<SalePriceEntity> {
    return request.post('/salePrice/update', data)
  },

  delete(id: number): Promise<void> {
    return request.post('/salePrice/delete', { id })
  },
}
