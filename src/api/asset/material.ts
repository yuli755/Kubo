import type { MaterialEntity, MaterialQuery } from './types'
import { request } from '@/api/axios'

export const materialApi = {
  getPage(params: MaterialQuery): Promise<{ list: MaterialEntity[], total: number }> {
    return request.post('/material/getPage', params)
  },

  getDetail(id: number): Promise<MaterialEntity> {
    return request.post('/material/getDetail', { id })
  },

  add(data: Partial<MaterialEntity>): Promise<MaterialEntity> {
    return request.post('/material/add', data)
  },

  update(data: Partial<MaterialEntity>): Promise<MaterialEntity> {
    return request.post('/material/update', data)
  },

  delete(id: number): Promise<void> {
    return request.post('/material/delete', { id })
  },
}
