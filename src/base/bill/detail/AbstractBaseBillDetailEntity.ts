import { BaseEntity } from '@/base/BaseEntity'
import { Field } from '@/framework'

/**
 * # 单据明细抽象基类（新底座）
 *
 * 明细挂在单据的 `details` 数组里，只有 `id` / `billId` / 数量是公共部分；
 * 具体字段（物料、供应商、来源库存…）由各域自己声明。
 *
 * 明细全都在**内存数组**里编辑（没有服务、没有分页），所以元数据只关心
 * 「这一列怎么显示」——`@Table` 由各域在字段上给。
 */
export abstract class AbstractBaseBillDetailEntity extends BaseEntity {
  /** 所属单据 id（新增明细时由页面预置，用户不可填） */
  @Field({ label: '单据 id' })
  billId?: number

  /** 数量 */
  abstract quantity: number

  /** 已完成数量（作业累加的结果） */
  abstract finishQuantity: number
}
