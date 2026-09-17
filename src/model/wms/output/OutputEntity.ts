import type { WebEnum } from '@airpower/web'
import { Field, Form, Model, Search, Table, Type } from '@airpower/web'
import { AbstractBaseBillEntity } from '@/base/bill/AbstractBaseBillEntity'
// 过渡态双注册（口径见 `MoveEntity`）：airpower 那套仍服务于还没迁的 channel/选择器，
// `@K*` 服务于批 4 起的 wms 页面。基类暂时仍是 airpower 的 `AbstractBaseBillEntity`。
import {
  getEnumLabel,
  Field as KField,
  Form as KForm,
  Model as KModel,
  Search as KSearch,
  Table as KTable,
  Type as KType,
} from '@/framework'
import { SaleEntity } from '@/model/channel/sale/SaleEntity'
import { MoveEntity } from '../move/MoveEntity'
import { OutputDetailEntity } from './OutputDetailEntity'
import { OutputStatusEnum } from './OutputStatusEnum'
import { OutputTypeEnum } from './OutputTypeEnum'

@Model({
  label: '出库单',
})
@KModel({
  label: '出库单',
  addChildPermission: 'add',
})
export class OutputEntity extends AbstractBaseBillEntity<OutputDetailEntity> {
  @KTable({ order: 99, force: true, minWidth: 180, copy: true })
  @KForm({ placeholder: '不填写按编码规则自动生成' })
  @KField({ label: '出库单号' })
  @KSearch()
  @Field({
    label: '出库单号',
  })
  declare billCode: string

  @Table({
    width: 100,
    color: true,
    force: true,
  })
  @Form({
    defaultValue: OutputTypeEnum.NORMAL.key,
    clearable: false,
    requiredNumber: true,
  })
  @Search()
  @KTable({ width: 110, order: 95, color: true, formatter: (row: never) => {
    const type = (row as { type?: number }).type
    return type === undefined ? '-' : (getEnumLabel(OutputTypeEnum, type) ?? '-')
  } })
  // 不给 `@KForm`：出库类型由来源单据（销售/移库）或页面预设决定，编辑器里不让用户改
  @KField({ label: '出库类型', dictionary: OutputTypeEnum })
  @KSearch({ width: 120 })
  @Field({
    label: '出库类型',
    dictionary: OutputTypeEnum,
  })
  type!: number

  @Table({
    width: 100,
    color: true,
    order: -80,
    force: true,
  })
  @KTable({ width: 100, order: -80, force: true, color: true })
  @KField({ label: '出库状态', dictionary: OutputStatusEnum })
  @KSearch({ width: 120 })
  @Field({
    label: '出库状态',
    dictionary: OutputStatusEnum,
  })
  status!: number

  @KField({ label: '出库明细' })
  @KType(OutputDetailEntity)
  @Field({
    label: '出库明细',
  })
  @Type(OutputDetailEntity, true)
  details: OutputDetailEntity[] = []

  // ── 基类字段的 K 侧声明（同 `InputEntity`）：列表要创建时间，表单/详情要驳回原因
  @KTable({ order: -90, width: 170, datetime: true })
  @KField({ label: '创建时间' })
  declare createTime: number

  @KTable({ order: -91, width: 170, datetime: true, hide: true })
  @KField({ label: '更新时间' })
  declare updateTime: number

  @KField({ label: '驳回原因' })
  declare rejectReason: string

  // ⚠️ airpower 侧的 label 历史上写成了「采购单」（实际是销售单）；K 侧按真实语义写「销售单」，
  //    airpower 那行只在 channel 未迁移期间可读，等批 5 删 airpower 装饰器时一并消失。
  @KField({ label: '销售单' })
  @KType(SaleEntity)
  @Field({
    label: '销售单',
  })
  @Type(SaleEntity)
  sale!: SaleEntity

  @KField({ label: '移库单' })
  @KType(MoveEntity)
  @Field({
    label: '移库单',
  })
  @Type(MoveEntity)
  move!: MoveEntity

  getAuditingStatus(): WebEnum {
    return OutputStatusEnum.AUDITING
  }

  getAuditedStatus(): WebEnum {
    return OutputStatusEnum.OUTPUTTING
  }

  getRejectedStatus(): WebEnum {
    return OutputStatusEnum.REJECTED
  }
}
