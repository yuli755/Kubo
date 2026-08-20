import type { RoutingEntity, RoutingQuery } from './types'
import { request } from '@/api/axios'

/**
 * # MES 生产工艺 API
 *
 * 与后端 airpower `@Api("routing")` 自动生成的端点对齐。
 * 后端 extends BaseController → 标准 CRUD + publish。
 */
export const routingApi = {
  /** 分页查询生产工艺 */
  getPage(params: RoutingQuery): Promise<{ list: RoutingEntity[], total: number }> {
    return request.post('/routing/getPage', params)
  },

  /** 获取生产工艺详情 */
  getDetail(id: number): Promise<RoutingEntity> {
    return request.post(`/routing/getDetail`, { id })
  },

  /** 新增生产工艺 */
  add(data: Partial<RoutingEntity>): Promise<RoutingEntity> {
    return request.post('/routing/add', data)
  },

  /** 修改生产工艺 */
  update(data: Partial<RoutingEntity>): Promise<RoutingEntity> {
    return request.post('/routing/update', data)
  },

  /** 发布生产工艺 */
  publish(data: { id: number }): Promise<void> {
    return request.post('/routing/publish', data)
  },
}
