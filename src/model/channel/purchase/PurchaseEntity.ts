import type { WebEnum } from '@airpower/web'
import { Field, Form, Model, Search, Table, Type } from '@airpower/web'
import { AbstractBaseBillEntity } from '@/base/bill/AbstractBaseBillEntity'
import {
  Field as KField,
  Form as KForm,
  Model as KModel,
  Search as KSearch,
  Table as KTable,
  Type as KType,
} from '@/framework'
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
@Model({
  label: '采购单',
})
@KModel({
  label: '采购单',
  addChildPermission: 'add',
})
export class PurchaseEntity extends AbstractBaseBillEntity<PurchaseDetailEntity> {
  @Field({
    label: '采购单号',
  })
  @KTable({ order: 99, minWidth: 180, force: true, copy: true })
  @KForm({ placeholder: '不填写按编码规则自动生成' })
  @KField({ label: '采购单号' })
  @KSearch()
  declare billCode: string

  @Table({})
  @Form({
    textarea: true,
    maxLength: 80,
    requiredString: true,
  })
  @Field({
    label: '采购事由',
  })
  @KTable({ minWidth: 220, order: 60 })
  @KForm({ textarea: true, maxLength: 80, requiredString: true })
  @KField({ label: '采购事由' })
  reason!: string

  @Table({
    width: 150,
    money: true,
    force: true,
  })
  @Form({
    suffixText: '元',
  })
  @Field({
    label: '总金额',
  })
  @KTable({ width: 150, order: 50, money: true, force: true, align: 'right' })
  @KForm({ number: true, precision: 2, suffixText: '元' })
  @KField({ label: '总金额' })
  totalPrice!: number

  @Table({
    width: 150,
    money: true,
    force: true,
  })
  @Form({
    suffixText: '元',
  })
  @Field({
    label: '实际金额',
  })
  @KTable({ width: 150, order: 40, money: true, force: true, align: 'right' })
  @KForm({ number: true, precision: 2, suffixText: '元' })
  @KField({ label: '实际金额' })
  totalRealPrice!: number

  @Table({
    width: 100,
    color: true,
    order: -80,
    force: true,
  })
  @Search({
    width: 120,
  })
  @Field({
    label: '采购状态',
    dictionary: PurchaseStatusEnum,
  })
  @KTable({ width: 110, order: -80, force: true, color: true })
  @KSearch({ width: 120 })
  @KField({ label: '采购状态', dictionary: PurchaseStatusEnum })
  status!: number

  @Field({
    label: '采购明细',
  })
  @Type(PurchaseDetailEntity, true)
  @KField({ label: '采购明细' })
  @KType(PurchaseDetailEntity)
  details: PurchaseDetailEntity[] = []

  // ── 基类（`BaseEntity` / `AbstractBaseBillEntity`）的字段在 K 注册表里没有条目，
  //    `declare` 重声明只为挂 `@K*`：K 列表要显示创建时间，K 详情要能看到驳回原因。
  @KTable({ order: -90, width: 170, datetime: true })
  @KField({ label: '创建时间' })
  declare createTime: number

  @KTable({ order: -91, width: 170, datetime: true, hide: true })
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
