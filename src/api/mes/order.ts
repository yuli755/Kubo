import type { BillAuditRequest, BillFinishRequest, BillRejectRequest, OrderEntity, OrderQuery, OrderReportRequest } from './types'
import { request } from '@/api/axios'

/**
 * # MES 生产订单 API
 *
 * 与后端 airpower `@Api("order")` 自动生成的端点对齐。
 * 后端 `@Extends(exclude = Curd.Delete)` → 无 delete 端点。
 * 额外暴露 BaseBillController 的 audit / reject / setBillDetailsAllFinished，
 * 以及 OrderController 特有的 start / pause / addOrderDetail。
 */
export const orderApi = {
  /** 分页查询生产订单 */
  getPage(params: OrderQuery): Promise<{ list: OrderEntity[], total: number }> {
    return request.get('/order/page', { params })
  },

  /** 获取生产订单详情 */
  getDetail(id: number): Promise<OrderEntity> {
    return request.get(`/order/${id}`)
  },

  /** 新增生产订单 */
  add(data: Partial<OrderEntity>): Promise<OrderEntity> {
    return request.post('/order', data)
  },

  /** 修改生产订单 */
  update(data: Partial<OrderEntity>): Promise<OrderEntity> {
    return request.put('/order', data)
  },

  /** 审核生产订单 */
  audit(data: BillAuditRequest): Promise<void> {
    return request.post('/order/audit', data)
  },

  /** 驳回生产订单 */
  reject(data: BillRejectRequest): Promise<void> {
    return request.post('/order/reject', data)
  },

  /** 完成所有明细 */
  setBillDetailsAllFinished(data: BillFinishRequest): Promise<void> {
    return request.post('/order/setBillDetailsAllFinished', data)
  },

  /** 开始生产 */
  start(data: { id: number }): Promise<void> {
    return request.post('/order/start', data)
  },

  /** 暂停生产 */
  pause(data: { id: number }): Promise<void> {
    return request.post('/order/pause', data)
  },

  /** 订单报工 */
  addOrderDetail(data: OrderReportRequest): Promise<void> {
    return request.post('/order/addOrderDetail', data)
  },
}
