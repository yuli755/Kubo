import type { StructureEntity } from './types'
import { request } from '@/api/axios'

/**
 * # Factory 生产单元 API
 *
 * 与后端 airpower `@Api("structure")` 自动生成的端点对齐。
 * 后端 extends BaseController → 标准 CRUD + publish。
 * 树形实体使用 `getList` 获取全量树数据。
 */
export const structureApi = {
  /** 获取生产单元树数据（全量，POST 内部 API） */
  getList(params?: Record<string, unknown>): Promise<StructureEntity[]> {
    return request.post('/structure/getList', params ?? {})
  },

  /** 获取生产单元详情 */
  getDetail(id: number): Promise<StructureEntity> {
    return request.post(`/structure/getDetail`, { id })
  },

  /** 新增生产单元 */
  add(data: Partial<StructureEntity>): Promise<StructureEntity> {
    return request.post('/structure/add', data)
  },

  /** 修改生产单元 */
  update(data: Partial<StructureEntity>): Promise<StructureEntity> {
    return request.post('/structure/update', data)
  },

  /** 删除生产单元 */
  delete(id: number): Promise<void> {
    return request.post('/structure/delete', { id })
  },

  /** 发布生产单元 */
  publish(data: { id: number }): Promise<void> {
    return request.post('/structure/publish', data)
  },
}
