import type { StorageEntity } from './types'
import { request } from '@/api/axios'

/**
 * # Factory 仓库 API
 *
 * 与后端 airpower `@Api("storage")` 自动生成的端点对齐。
 * 后端 extends BaseController → 标准 CRUD + publish。
 * 树形实体使用 `getList` 获取全量树数据。
 */
export const storageApi = {
  /** 获取仓库树数据（全量，POST 内部 API） */
  getList(params?: Record<string, unknown>): Promise<StorageEntity[]> {
    return request.post('/storage/getList', params ?? {})
  },

  /** 获取仓库详情 */
  getDetail(id: number): Promise<StorageEntity> {
    return request.get(`/storage/${id}`)
  },

  /** 新增仓库 */
  add(data: Partial<StorageEntity>): Promise<StorageEntity> {
    return request.post('/storage', data)
  },

  /** 修改仓库 */
  update(data: Partial<StorageEntity>): Promise<StorageEntity> {
    return request.put('/storage', data)
  },

  /** 删除仓库 */
  delete(id: number): Promise<void> {
    return request.post('/storage/delete', { id })
  },

  /** 发布仓库 */
  publish(data: { id: number }): Promise<void> {
    return request.post('/storage/publish', data)
  },
}
