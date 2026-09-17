import type { WebEnum } from '@airpower/web'
import { Field, Form, Model, Search, Table, Type } from '@airpower/web'
import { AbstractBaseBillEntity } from '@/base/bill/AbstractBaseBillEntity'
// 过渡态双注册（口径见 `MoveEntity`）：airpower 那套仍服务于还没迁的 channel/选择器，
// `@K*` 服务于批 4 起的 wms 页面。基类暂时仍是 airpower 的 `AbstractBaseBillEntity`，
// 切 `KEntity` 与这里的双注册收尾一起做。
import {
  getEnumLabel,
  Field as KField,
  Form as KForm,
  Model as KModel,
  Search as KSearch,
  Table as KTable,
  Type as KType,
} from '@/framework'
import { PurchaseEntity } from '@/model/channel/purchase/PurchaseEntity'
import { StorageEntity } from '@/model/factory/storage/StorageEntity'
import { MoveEntity } from '../move/MoveEntity'
import { InputDetailEntity } from './InputDetailEntity'
import { InputStatusEnum } from './InputStatusEnum'
import { InputTypeEnum } from './InputTypeEnum'

@Model({
  label: '入库单',
})
@KModel({
  label: '入库单',
  addChildPermission: 'add',
})
export class InputEntity extends AbstractBaseBillEntity<InputDetailEntity> {
  @Field({
    label: '入库单号',
  })
  @KTable({ order: 99, force: true, minWidth: 180, copy: true })
  @KForm({ placeholder: '不填写按编码规则自动生成' })
  @KField({ label: '入库单号' })
  @KSearch()
  declare billCode: string

  @Table({
    width: 100,
    color: true,
    force: true,
  })
  @Form({
    defaultValue: InputTypeEnum.NORMAL.key,
    clearable: false,
    requiredNumber: true,
  })
  @Search({
    width: 120,
  })
  @KTable({ width: 110, order: 95, color: true, formatter: (row: never) => {
    const type = (row as { type?: number }).type
    return type === undefined ? '-' : (getEnumLabel(InputTypeEnum, type) ?? '-')
  } })
  // 不给 `@KForm`：入库类型由来源单据（采购/移库）或页面预设决定，编辑器里不让用户改；
  // 新增时页面用 `preset` 带 `type = 普通入库`（与旧编辑器 `type ??= NORMAL` 同义）
  @KField({ label: '入库类型', dictionary: InputTypeEnum })
  @KSearch({ width: 120 })
  @Field({
    label: '入库类型',
    dictionary: InputTypeEnum,
  })
  type!: number

  @Table({
    width: 100,
    color: true,
    order: -80,
    force: true,
  })
  @KTable({ width: 100, order: -80, force: true, color: true })
  @KField({ label: '入库状态', dictionary: InputStatusEnum })
  @KSearch({ width: 120 })
  @Field({
    label: '入库状态',
    dictionary: InputStatusEnum,
  })
  status!: number

  // 不给 `@KForm`：仓库不在入库单主表单里选，而是在「确认入库」（作业）时按明细指定 ——
  // 表单里多一个必填项会让新增流程根本提交不出去（旧页面同样没有这个字段）。
  @KField({ label: '仓库' })
  @KType(StorageEntity)
  @Field({
    label: '仓库',
  })
  @Type(StorageEntity)
  storage!: StorageEntity

  @KField({ label: '入库明细' })
  @KType(InputDetailEntity)
  @Field({
    label: '入库明细',
  })
  @Type(InputDetailEntity, true)
  details: InputDetailEntity[] = []

  // ── 基类字段（`AbstractBaseBillEntity`）在 K 注册表里没有条目，`declare` 重声明只为挂 `@K*`：
  //    K 列表要显示创建时间，K 表单/详情要能看到驳回原因。
  @KTable({ order: -90, width: 170, datetime: true })
  @KField({ label: '创建时间' })
  declare createTime: number

  @KTable({ order: -91, width: 170, datetime: true, hide: true })
  @KField({ label: '更新时间' })
  declare updateTime: number

  @KField({ label: '驳回原因' })
  declare rejectReason: string

  @KField({ label: '采购单' })
  @KType(PurchaseEntity)
  @Field({
    label: '采购单',
  })
  @Type(PurchaseEntity)
  purchase!: PurchaseEntity

  @KField({ label: '移库单' })
  @KType(MoveEntity)
  @Field({
    label: '移库单',
  })
  @Type(MoveEntity)
  move!: MoveEntity

  getAuditingStatus(): WebEnum {
    return InputStatusEnum.AUDITING
  }

  getAuditedStatus(): WebEnum {
    return InputStatusEnum.INPUTTING
  }

  getRejectedStatus(): WebEnum {
    return InputStatusEnum.REJECTED
  }
}
