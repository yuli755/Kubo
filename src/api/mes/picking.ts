import type { BillAuditRequest, BillFinishRequest, BillRejectRequest, PickingEntity, PickingQuery } from './types'
import { request } from '@/api/axios'

/**
 * # MES 领料单 API
 *
 * 与后端 airpower `@Api("picking")` 自动生成的端点对齐。
 * 额外暴露 BaseBillController 的 audit / reject / setBillDetailsAllFinished。
 */
export const pickingApi = {
  /** 分页查询领料单 */
  getPage(params: PickingQuery): Promise<{ list: PickingEntity[], total: number }> {
    return request.get('/picking/page', { params })
  },

  /** 获取领料单详情 */
  getDetail(id: number): Promise<PickingEntity> {
    return request.get(`/picking/${id}`)
  },

  /** 新增领料单 */
  add(data: Partial<PickingEntity>): Promise<PickingEntity> {
    return request.post('/picking', data)
  },

  /** 修改领料单 */
  update(data: Partial<PickingEntity>): Promise<PickingEntity> {
    return request.put('/picking', data)
  },

  /** 审核领料单 */
  audit(data: BillAuditRequest): Promise<void> {
    return request.post('/picking/audit', data)
  },

  /** 驳回领料单 */
  reject(data: BillRejectRequest): Promise<void> {
    return request.post('/picking/reject', data)
  },

  /** 完成所有明细 */
  setBillDetailsAllFinished(data: BillFinishRequest): Promise<void> {
    return request.post('/picking/setBillDetailsAllFinished', data)
  },
}
