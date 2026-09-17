import type { IIdentifiableEntity } from './types'
import { AbstractCurdService } from './http'

/**
 * # 单据类服务基类（新底座）
 *
 * 单据（入库 / 出库 / 移库 / 采购 / 销售）比普通实体多四个动作：审核、驳回、
 * 添加完成数量、一键完成全部明细。它们不是 CRUD，但**每个单据域都要写一遍** ——
 * 放在基类里，域服务只剩 `entityClass` + `baseUrl` 两行。
 *
 * ## 与 airpower `AbstractBaseBillService` 的关系
 * 那份还在（`src/base/bill/`），未迁移的 wms/channel 页面继续用它；两者 baseUrl 与
 * 端点名完全一致（`audit` / `reject` / `addFinish` / `setBillDetailsAllFinished`），
 * 所以过渡期两份实现指向同一后端接口，不存在数据漂移。
 *
 * ## 两个容易写错的地方（都来自后端实际契约）
 * 1. **`addFinish` 的载荷是「明细」不是「单据」**：后端按明细 id 从库里读来源库存，
 *    前端只需要回 `{ id, quantity }`（外加出库场景的 `inventory.id`）。传整张单据会被忽略。
 * 2. **完成数量是「本次数量」不是「累计数量」**：`afterDetailFinishAdded` 做的是累加，
 *    传累计值会直接把明细数量翻倍 —— 页面默认值给「待完成数量」（总量 − 已完成）。
 */
export abstract class AbstractBillService<
  B extends IIdentifiableEntity,
  D extends IIdentifiableEntity = IIdentifiableEntity,
> extends AbstractCurdService<B> {
  /** 审核 */
  protected urlAudit = 'audit'

  /** 驳回 */
  protected urlReject = 'reject'

  /** 添加完成数量（载荷是明细） */
  protected urlAddFinish = 'addFinish'

  /** 一键完成全部明细 */
  protected urlSetBillDetailsAllFinished = 'setBillDetailsAllFinished'

  /** 审核单据 */
  async audit(bill: B): Promise<void> {
    await this.api(this.urlAudit).request(bill)
  }

  /** 驳回单据 */
  async reject(bill: B, rejectReason: string): Promise<void> {
    await this.api(this.urlReject).request({ id: bill.id, rejectReason })
  }

  /** 添加完成数量（本次数量；载荷 = 明细实体） */
  async addDetailFinishQuantity(detail: D, apiUrl = this.urlAddFinish): Promise<void> {
    await this.api(apiUrl).request(detail)
  }

  /** 一键把该单据所有明细标记为已完成 */
  async setBillDetailsAllFinished(bill: B): Promise<void> {
    await this.api(this.urlSetBillDetailsAllFinished).request(bill)
  }
}
