import type { PurchaseDetailEntity } from './PurchaseDetailEntity'
import { AbstractBillService } from '@/framework'
import { PurchaseEntity } from './PurchaseEntity'

/**
 * # 采购单服务（新底座）
 *
 * 三个单据动作（审核 / 驳回 / 添加完成数量）由 `AbstractBillService` 提供，
 * 端点名与后端 `BaseBillController` 逐字一致（后端只有这三个 `@PostMapping`）。
 *
 * ⚠️ 「作业」（`addFinish`）的载荷是**明细**（`{ id, quantity, billId }`），
 * 数量是**本次**采购数量、不是累计（后端做累加）—— 页面默认值给
 * 「待采购数量 = 采购数量 − 已采购数量」。
 */
export class KPurchaseService extends AbstractBillService<PurchaseEntity, PurchaseDetailEntity> {
  entityClass = PurchaseEntity
  baseUrl = 'purchase'
}
