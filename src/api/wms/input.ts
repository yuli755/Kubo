import type { BillAuditRequest, BillFinishRequest, BillRejectRequest, InputEntity, InputQuery } from './types'
import { request } from '@/api/axios'

/**
 * # WMS 入库单 API
 *
 * 与后端 airpower `@Api("input")` 自动生成的端点对齐。
 * 后端 `@Extends(exclude = Curd.Delete)` → 无 delete 端点。
 * 额外暴露 BaseBillController 的 audit / reject / setBillDetailsAllFinished / addFinish。
 */
export const inputApi = {
  /** 分页查询入库单 */
  getPage(params: InputQuery): Promise<{ list: InputEntity[], total: number }> {
    return request.post('/input/getPage', params)
  },

  /** 获取入库单详情 */
  getDetail(id: number): Promise<InputEntity> {
    return request.post(`/input/getDetail`, { id })
  },

  /** 新增入库单 */
  add(data: Partial<InputEntity>): Promise<InputEntity> {
    return request.post('/input/add', data)
  },

  /** 修改入库单 */
  update(data: Partial<InputEntity>): Promise<InputEntity> {
    return request.post('/input/update', data)
  },

  /** 审核入库单 */
  audit(data: BillAuditRequest): Promise<void> {
    return request.post('/input/audit', data)
  },

  /** 驳回入库单 */
  reject(data: BillRejectRequest): Promise<void> {
    return request.post('/input/reject', data)
  },

  /** 完成所有明细 */
  setBillDetailsAllFinished(data: BillFinishRequest): Promise<void> {
    return request.post('/input/setBillDetailsAllFinished', data)
  },

  /** 添加完成数量 */
  addDetailFinishQuantity(data: { id: number, finishQuantity: number }): Promise<void> {
    return request.post('/input/addFinish', data)
  },
}
