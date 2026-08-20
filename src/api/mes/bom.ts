import type { BomEntity, BomQuery } from './types'
import { request } from '@/api/axios'

/**
 * # MES 生产配方 API
 *
 * 与后端 airpower `@Api("bom")` 自动生成的端点对齐。
 * 后端 extends BaseController → 标准 CRUD + publish。
 */
export const bomApi = {
  /** 分页查询生产配方 */
  getPage(params: BomQuery): Promise<{ list: BomEntity[], total: number }> {
    return request.post('/bom/getPage', params)
  },

  /** 获取生产配方详情 */
  getDetail(id: number): Promise<BomEntity> {
    return request.post(`/bom/getDetail`, { id })
  },

  /** 新增生产配方 */
  add(data: Partial<BomEntity>): Promise<BomEntity> {
    return request.post('/bom/add', data)
  },

  /** 修改生产配方 */
  update(data: Partial<BomEntity>): Promise<BomEntity> {
    return request.post('/bom/update', data)
  },

  /** 发布生产配方 */
  publish(data: { id: number }): Promise<void> {
    return request.post('/bom/publish', data)
  },
}
