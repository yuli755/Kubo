import type { RoleEntity, RoleQuery } from './types'
import { request } from '@/api/axios'

export const roleApi = {
  getPage(params: RoleQuery): Promise<{ list: RoleEntity[], total: number }> {
    return request.post('/role/getPage', params)
  },

  getDetail(id: number): Promise<RoleEntity> {
    return request.post('/role/getDetail', { id })
  },

  add(data: Partial<RoleEntity>): Promise<RoleEntity> {
    return request.post('/role/add', data)
  },

  update(data: Partial<RoleEntity>): Promise<RoleEntity> {
    return request.post('/role/update', data)
  },

  delete(id: number): Promise<void> {
    return request.post('/role/delete', { id })
  },

  disable(id: number): Promise<void> {
    return request.post('/role/disable', { id })
  },

  enable(id: number): Promise<void> {
    return request.post('/role/enable', { id })
  },

  authorizeMenu(id: number, menuList: { id: number }[]): Promise<void> {
    return request.post('/role/authorizeMenu', { id, menuList })
  },

  authorizePermission(id: number, permissionList: { id: number }[]): Promise<void> {
    return request.post('/role/authorizePermission', { id, permissionList })
  },
}