import type { WebEnum } from '@/framework'
import { AbstractBaseBillEntity } from '@/base/bill/AbstractBaseBillEntity'
import { Field as KField, Form as KForm, Model as KModel, Search as KSearch, Table as KTable, Type as KType } from '@/framework'
import { PurchaseDetailEntity } from './PurchaseDetailEntity'
import { PurchaseStatusEnum } from './PurchaseStatusEnum'

/**
 * # 采购单（F4 批 5 起双注册）
 *
 * K 侧（`@K*`）服务于采购单页与入库单详情里的「来源采购单」；airpower 那套留到 F5 收尾。
 *
 * 与移库单同一套口径：
 * - 状态**不给 `@KForm`** —— 状态由状态机（审核 / 驳回 / 作业）驱动，表单里出现就是「用户能改状态」；
 * - `details` 只给 `@KField` + `@KType`：明细由 `KDetailTable` 在编辑器里渲染，
 *   不是 `KFormDialog` 的表单字段。
 */
@KModel({
  label: '采购单',
  addChildPermission: 'add',
})
export class PurchaseEntity extends AbstractBaseBillEntity<PurchaseDetailEntity> {
  /**
   * ### 列宽（口径见 `src/framework/types.ts`）
   *
   * 实测本页声明宽合计 1326px、可用只有 1131px ⇒ 横向滚动，而每列内容都远小于列宽：
   * 采购单号 180/105、采购事由 220/162、总金额 150/70、实际金额 150/70、采购状态 110/42、
   * 创建时间 170/129。操作列因为挂了 5 个文字按钮（查看/审核/驳回/再来一单/编辑）实测就要 290px，
   * 是刚性开销（`.k-table__actions` 内容 265px + 单元格左右 padding 24px），压不下来。
   * 于是从数据列上收：单号/金额/状态/时间按内容定宽，只有「采购事由」留弹性。
   *
   * `实际金额` 改为默认隐藏：入库完成前它与「总金额」恒等（编辑器 `editor.vue:67-71,94-95`
   * 就是把空值直接填成 `totalAmount`），列表上并排放两个一样的金额是纯冗余；
   * 详情抽屉的 `headerFields` 仍然展示它，列选择器里也能勾回来（`hide` 是布局取舍，不是权限控制）。
   */
  @KTable({ order: 99, width: 150, force: true, copy: true })
  @KForm({ placeholder: '不填写按编码规则自动生成' })
  @KField({ label: '采购单号' })
  @KSearch()
  declare billCode: string

  @KTable({ minWidth: 170, order: 60 })
  @KForm({ textarea: true, maxLength: 80, requiredString: true })
  @KField({ label: '采购事由' })
  reason!: string

  @KTable({ width: 120, order: 50, money: true, force: true, align: 'right' })
  @KForm({ number: true, precision: 2, suffixText: '元' })
  @KField({ label: '总金额' })
  totalPrice!: number

  @KTable({ width: 120, order: 40, money: true, align: 'right', hide: true })
  @KForm({ number: true, precision: 2, suffixText: '元' })
  @KField({ label: '实际金额' })
  totalRealPrice!: number

  @KTable({ width: 100, order: -80, force: true, color: true })
  @KSearch({ width: 120 })
  @KField({ label: '采购状态', dictionary: PurchaseStatusEnum })
  status!: number

  @KField({ label: '采购明细' })
  @KType(PurchaseDetailEntity)
  details: PurchaseDetailEntity[] = []

  // ── 基类（`BaseEntity` / `AbstractBaseBillEntity`）的字段在 K 注册表里没有条目，
  //    `declare` 重声明只为挂 `@K*`：K 列表要显示创建时间，K 详情要能看到驳回原因。
  @KTable({ order: -90, width: 160, datetime: true })
  @KField({ label: '创建时间' })
  declare createTime: number

  @KTable({ order: -91, width: 160, datetime: true, hide: true })
  @KField({ label: '更新时间' })
  declare updateTime: number

  @KField({ label: '驳回原因' })
  declare rejectReason: string

  getAuditingStatus(): WebEnum {
    return PurchaseStatusEnum.AUDITING
  }

  getAuditedStatus(): WebEnum {
    return PurchaseStatusEnum.PURCHASING
  }

  getRejectedStatus(): WebEnum {
    return PurchaseStatusEnum.REJECTED
  }
}
