import type { BillAuditRequest, BillFinishRequest, BillRejectRequest, PlanEntity, PlanQuery } from './types'
import { request } from '@/api/axios'

/**
 * # MES 生产计划 API
 *
 * 与后端 airpower `@Api("plan")` 自动生成的端点对齐。
 * 后端 `@Extends(exclude = Delete)` → 无 delete 端点。
 * 额外暴露 BaseBillController 的 audit / reject / setBillDetailsAllFinished。
 */
export const planApi = {
  /** 分页查询生产计划 */
  getPage(params: PlanQuery): Promise<{ list: PlanEntity[], total: number }> {
    return request.post('/plan/getPage', params)
  },

  /** 获取生产计划详情 */
  getDetail(id: number): Promise<PlanEntity> {
    return request.post(`/plan/getDetail`, { id })
  },

  /** 新增生产计划 */
  add(data: Partial<PlanEntity>): Promise<PlanEntity> {
    return request.post('/plan/add', data)
  },

  /** 修改生产计划 */
  update(data: Partial<PlanEntity>): Promise<PlanEntity> {
    return request.post('/plan/update', data)
  },

  /** 审核生产计划 */
  audit(data: BillAuditRequest): Promise<void> {
    return request.post('/plan/audit', data)
  },

  /** 驳回生产计划 */
  reject(data: BillRejectRequest): Promise<void> {
    return request.post('/plan/reject', data)
  },

  /** 完成所有明细 */
  setBillDetailsAllFinished(data: BillFinishRequest): Promise<void> {
    return request.post('/plan/setBillDetailsAllFinished', data)
  },
}
