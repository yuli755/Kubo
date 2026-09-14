import type { BillAuditRequest, BillFinishRequest, BillRejectRequest, MoveEntity, MoveQuery } from './types'
import { request } from '@/api/axios'

/**
 * # WMS 移库单 API
 *
 * 与后端 airpower `@Api("move")` 自动生成的端点对齐。
 * 后端 `@Extends(exclude = Curd.Delete)` → 无 delete 端点。
 * 额外暴露 BaseBillController 的 audit / reject / setBillDetailsAllFinished / addFinish。
 */
export const moveApi = {
  /** 分页查询移库单 */
  getPage(params: MoveQuery): Promise<{ list: MoveEntity[], total: number }> {
    return request.post('/move/getPage', params)
  },

  /** 获取移库单详情 */
  getDetail(id: number): Promise<MoveEntity> {
    return request.post(`/move/getDetail`, { id })
  },

  /** 新增移库单 */
  add(data: Partial<MoveEntity>): Promise<MoveEntity> {
    return request.post('/move/add', data)
  },

  /** 修改移库单 */
  update(data: Partial<MoveEntity>): Promise<MoveEntity> {
    return request.post('/move/update', data)
  },

  /** 审核移库单 */
  audit(data: BillAuditRequest): Promise<void> {
    return request.post('/move/audit', data)
  },

  /** 驳回移库单 */
  reject(data: BillRejectRequest): Promise<void> {
    return request.post('/move/reject', data)
  },

  /** 完成所有明细 */
  setBillDetailsAllFinished(data: BillFinishRequest): Promise<void> {
    return request.post('/move/setBillDetailsAllFinished', data)
  },

  /**
   * 添加完成数量
   *
   * 阶段 E-H1：后端 `MoveService.afterDetailFinishAdded` 以库中明细为准取
   * 来源库存，载荷只需 `id` 与本次 `quantity`（旧字段名 finishQuantity 会
   * 被绑定到历史完成量，本次数量恒为 null）。
   */
  addDetailFinishQuantity(data: { id: number, quantity: number }): Promise<void> {
    return request.post('/move/addFinish', data)
  },
}
