import type { InputDetailEntity } from './InputDetailEntity'
import { AbstractBillService } from '@/framework'
import { InputEntity } from './InputEntity'

/**
 * # 入库单服务（新底座）
 *
 * 四个单据动作（审核 / 驳回 / 添加完成数量 / 一键完成）由 `AbstractBillService` 提供，
 * 这里只剩接口地址 —— 与旧 `InputService`（airpower `AbstractBaseBillService`）同 `baseUrl`，
 * 端点名逐字一致。
 *
 * ⚠️ 「确认入库」（`addFinish`）的载荷是**明细**，且必须带 `storage.id`（入库目标仓库）与
 * **本次**数量 `quantity`（后端做累加）：见 `input/component/addFinishEditor.vue`。
 */
export class KInputService extends AbstractBillService<InputEntity, InputDetailEntity> {
  entityClass = InputEntity
  baseUrl = 'input'
}
