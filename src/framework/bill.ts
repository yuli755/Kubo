import type { IIdentifiableEntity } from './types'
import { AbstractCurdService } from './http'

/**
 * # 单据类服务基类（新底座）
 *
 * 单据（入库 / 出库 / 移库 / 采购 / 销售）比普通实体多三个动作：审核、驳回、
 * 添加完成数量。它们不是 CRUD，但**每个单据域都要写一遍** ——
 * 放在基类里，域服务只剩 `entityClass` + `baseUrl` 两行。
 *
 * ## 为什么这里没有「一键完成全部明细」
 * 曾经有：`urlSetBillDetailsAllFinished = 'setBillDetailsAllFinished'` + 同名方法。
 * 但后端 `BaseBillController` 只注册了 `audit` / `reject` / `addFinish` 三个 `@PostMapping`，
 * `setBillDetailsAllFinished` 是 `AbstractBaseBillService` 的**服务内部方法**
 * （由 `BaseBillDetailService.addFinishQuantity` 在最后一条明细完成时自动调用），
 * 从来不是接口。前端调用它只会拿到 HTTP 404，而 404 响应体没有 `code`/`message`，
 * 页面上表现为一句泛化的「请求失败」。
 *
 * 更要紧的是**它不该是接口**：「把全部明细标记完成」在后端与库存搬运是同一段事务
 * （`afterDetailFinishAdded` 累加完成量并触发出入库），单独开一个端点等于允许客户端
 * 在不动库存的情况下把单据改成已完成。所以这里删掉方法，而不是给后端补路由。
 * `contract:check`（`infra/scripts/check-api-contract.mjs` 规则 C）会拦住它再长回来。
 *
 * ## 与 airpower `AbstractBaseBillService` 的关系
 * 那份还在（`src/base/bill/`），未迁移的 wms/channel 页面继续用它；两者 baseUrl 与
 * 端点名完全一致（`audit` / `reject` / `addFinish`），
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
