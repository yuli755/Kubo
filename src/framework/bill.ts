import type { IIdentifiableEntity } from './types'
import { AbstractCurdService } from './http'

/**
 * # 单据类服务基类（新底座）
 *
 * 单据（入库 / 出库 / 移库 / 采购 / 销售）比普通实体多**三个**动作：审核、驳回、
 * 添加完成数量。它们不是 CRUD，但**每个单据域都要写一遍** ——
 * 放在基类里，域服务只剩 `entityClass` + `baseUrl` 两行。
 *
 * ## 只有三个动作：没有「一键完成」
 * 后端 `BaseBillController` 暴露的是 `audit` / `reject` / `addFinish`，
 * 明细必须**逐条作业**（`addFinish` 带本次数量）直到最后一条完成 —— 此时后端
 * `AbstractBaseBillService#setBillDetailsAllFinished` 这个**内部回调**才会把单据推进到终态。
 *
 * 2026-09-19 删掉了前端基类里那个同名方法：它调的是 `POST /{baseUrl}/setBillDetailsAllFinished`，
 * 而后端从来没有这个映射（`grep '@PostMapping("setBillDetailsAllFinished")'` = 0），
 * 页面也没有调用点 —— 一个"看起来存在"的端点，谁接谁 404。
 * 想要真正的「一键完成」= 后端按剩余数量批量 `addFinish`（属能力建设，不是前端能自己凑出来的）。
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
}
