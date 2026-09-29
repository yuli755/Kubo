import type { MoveDetailEntity } from './MoveDetailEntity'
import { AbstractBillService } from '@/framework'
import { MoveEntity } from './MoveEntity'

/**
 * # 移库单服务（新底座）
 *
 * 移库单的三个单据动作（审核 / 驳回 / 添加完成数量）由 `AbstractBillService` 提供，
 * 这里只剩接口地址 —— 与旧 `MoveService`（airpower `AbstractBaseBillService`）同 `baseUrl`，
 * 端点名也逐字一致（`audit` / `reject` / `addFinish`；后端没有「一键完成」这个路由，
 * 它是服务内部方法，见 `AbstractBillService` 的头注释）。
 *
 * ⚠️ 「作业」（`addFinish`）的载荷是**明细**（`{ id, quantity }`，出库场景另带 `inventory.id`），
 * 后端按明细 id 自己找来源库存/目标库位；前端不要把整张单据传过去。
 */
export class KMoveService extends AbstractBillService<MoveEntity, MoveDetailEntity> {
  entityClass = MoveEntity
  baseUrl = 'move'
}
