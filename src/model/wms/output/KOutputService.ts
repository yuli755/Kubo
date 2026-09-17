import type { OutputDetailEntity } from './OutputDetailEntity'
import { AbstractBillService } from '@/framework'
import { OutputEntity } from './OutputEntity'

/**
 * # 出库单服务（新底座）
 *
 * 同 `KInputService`：单据动作由 `AbstractBillService` 提供，`baseUrl = 'output'`。
 *
 * ⚠️ 「确认出库」（`addFinish`）的载荷是**明细**，必须带 `inventory.id`（扣减来源）与
 * **本次**数量 `quantity`（后端做累加）：见 `output/component/addFinishEditor.vue`。
 */
export class KOutputService extends AbstractBillService<OutputEntity, OutputDetailEntity> {
  entityClass = OutputEntity
  baseUrl = 'output'
}
