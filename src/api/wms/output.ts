import type { BillAuditRequest, BillFinishRequest, BillRejectRequest, OutputEntity, OutputQuery } from './types'
import { request } from '@/api/axios'

/**
 * # WMS 出库单 API
 *
 * 与后端 airpower `@Api("output")` 自动生成的端点对齐。
 * 后端 `@Extends(exclude = Curd.Delete)` → 无 delete 端点。
 * 额外暴露 BaseBillController 的 audit / reject / setBillDetailsAllFinished / addFinish。
 */
export const outputApi = {
  /** 分页查询出库单 */
  getPage(params: OutputQuery): Promise<{ list: OutputEntity[], total: number }> {
    return request.post('/output/getPage', params)
  },

  /** 获取出库单详情 */
  getDetail(id: number): Promise<OutputEntity> {
    return request.post(`/output/getDetail`, { id })
  },

  /** 新增出库单 */
  add(data: Partial<OutputEntity>): Promise<OutputEntity> {
    return request.post('/output/add', data)
  },

  /** 修改出库单 */
  update(data: Partial<OutputEntity>): Promise<OutputEntity> {
    return request.post('/output/update', data)
  },

  /** 审核出库单 */
  audit(data: BillAuditRequest): Promise<void> {
    return request.post('/output/audit', data)
  },

  /** 驳回出库单 */
  reject(data: BillRejectRequest): Promise<void> {
    return request.post('/output/reject', data)
  },

  /** 完成所有明细 */
  setBillDetailsAllFinished(data: BillFinishRequest): Promise<void> {
    return request.post('/output/setBillDetailsAllFinished', data)
  },

  /**
   * 添加完成数量
   *
   * 阶段 E-H1：后端 `OutputService.afterDetailFinishAdded` 从提交载荷读取
   * `quantity`（本次累加的完成数量）与 `inventory.id`（出库来源库存），
   * 缺库存会直接 NPE（500）。旧签名传 `finishQuantity` 同理不可用。
   */
  addDetailFinishQuantity(data: { id: number, quantity: number, inventory: { id: number } }): Promise<void> {
    return request.post('/output/addFinish', data)
  },
}
