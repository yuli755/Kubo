import type { UserEntity, UserQuery } from './types'
import { request } from '@/api/axios'

export const userApi = {
  getPage(params: UserQuery): Promise<{ list: UserEntity[], total: number }> {
    return request.post('/user/getPage', params)
  },

  getDetail(id: number): Promise<UserEntity> {
    return request.post('/user/getDetail', { id })
  },

  add(data: Partial<UserEntity>): Promise<UserEntity> {
    return request.post('/user/add', data)
  },

  update(data: Partial<UserEntity>): Promise<UserEntity> {
    return request.post('/user/update', data)
  },

  delete(id: number): Promise<void> {
    return request.post('/user/delete', { id })
  },

  disable(id: number): Promise<void> {
    return request.post('/user/disable', { id })
  },

  enable(id: number): Promise<void> {
    return request.post('/user/enable', { id })
  },
}
