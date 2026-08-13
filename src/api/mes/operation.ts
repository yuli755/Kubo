import type { OperationEntity, OperationQuery } from './types'
import { request } from '@/api/axios'

/**
 * # MES 工序 API
 *
 * 与后端 airpower `@Api("operation")` 自动生成的端点对齐。
 * 后端 extends BaseController → 标准 CRUD（无 publish）。
 */
export const operationApi = {
  /** 分页查询工序 */
  getPage(params: OperationQuery): Promise<{ list: OperationEntity[], total: number }> {
    return request.get('/operation/page', { params })
  },

  /** 获取工序详情 */
  getDetail(id: number): Promise<OperationEntity> {
    return request.get(`/operation/${id}`)
  },

  /** 新增工序 */
  add(data: Partial<OperationEntity>): Promise<OperationEntity> {
    return request.post('/operation', data)
  },

  /** 修改工序 */
  update(data: Partial<OperationEntity>): Promise<OperationEntity> {
    return request.put('/operation', data)
  },
}
