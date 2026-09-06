import type { DepartmentEntity, DepartmentQuery } from './types'
import { request } from '@/api/axios'

export const departmentApi = {
  getPage(params: DepartmentQuery): Promise<{ list: DepartmentEntity[], total: number }> {
    return request.post('/department/getPage', params)
  },

  getTree(): Promise<DepartmentEntity[]> {
    return request.post('/department/getTree')
  },

  getDetail(id: number): Promise<DepartmentEntity> {
    return request.post('/department/getDetail', { id })
  },

  add(data: Partial<DepartmentEntity>): Promise<DepartmentEntity> {
    return request.post('/department/add', data)
  },

  update(data: Partial<DepartmentEntity>): Promise<DepartmentEntity> {
    return request.post('/department/update', data)
  },

  delete(id: number): Promise<void> {
    return request.post('/department/delete', { id })
  },
}
