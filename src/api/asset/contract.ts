import type { ContractEntity, ContractQuery } from './types'
import { request } from '@/api/axios'

export const contractApi = {
  getPage(params: ContractQuery): Promise<{ list: ContractEntity[], total: number }> {
    return request.post('/contract/getPage', params)
  },

  getDetail(id: number): Promise<ContractEntity> {
    return request.post('/contract/getDetail', { id })
  },

  add(data: Partial<ContractEntity>): Promise<ContractEntity> {
    return request.post('/contract/add', data)
  },

  update(data: Partial<ContractEntity>): Promise<ContractEntity> {
    return request.post('/contract/update', data)
  },

  delete(id: number): Promise<void> {
    return request.post('/contract/delete', { id })
  },

  enforce(data: { id: number }): Promise<void> {
    return request.post('/contract/enforce', data)
  },

  stop(data: { id: number }): Promise<void> {
    return request.post('/contract/stop', data)
  },
}