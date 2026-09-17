import { Field, Form, Model, Table, Type } from '@airpower/web'
import { AbstractBaseBillDetailEntity } from '@/base/bill/detail/AbstractBaseBillDetailEntity'
import { Field as KField, Form as KForm, Model as KModel, Table as KTable, Type as KType } from '@/framework'
import { MaterialEntity } from '@/model/asset/material/MaterialEntity'
import { CustomerEntity } from '../customer/CustomerEntity'

/** 明细里的物料是嵌套对象（列表列渲染器读 `row.material.*`） */
function materialPart(row: unknown, part: 'code' | 'name'): string {
  const material = (row as { material?: Record<string, string> }).material
  return material?.[part] ?? ''
}

/**
 * # 销售明细
 *
 * F4 批 5 起双注册：K 侧服务于销售单编辑器的明细表与详情弹窗。
 * 明细仍然保留 `customer`（旧契约：明细编辑器用它解析客户专属价），
 * 因此这里连客户对象一起做了 `@KType` —— K 侧提交/回填时不至于把它丢掉。
 */
@Model({
  label: '销售明细',
})
@KModel({
  label: '销售明细',
})
export class SaleDetailEntity extends AbstractBaseBillDetailEntity {
  @Form({
    requiredPayload: true,
  })
  @Type(MaterialEntity)
  @KForm({ requiredPayload: true })
  @KType(MaterialEntity)
  @KField({ label: '物料' })
  material!: MaterialEntity

  @Table({
    formatter: row => row.material.code,
    copy: true,
  })
  @Field({
    label: '物料编码',
  })
  @KTable({ order: 90, minWidth: 140, force: true, copy: true, formatter: (row: never) => materialPart(row, 'code') })
  @KField({ label: '物料编码' })
  materialCode!: string

  @Table({
    formatter: row => row.material.name,
    copy: true,
  })
  @Field({
    label: '物料名称',
  })
  @KTable({ order: 80, minWidth: 180, force: true, copy: true, formatter: (row: never) => materialPart(row, 'name') })
  @KField({ label: '物料名称' })
  materialName!: string

  @Form({
    requiredNumber: true,
  })
  @Field({
    label: '物料',
  })
  @KField({ label: '物料' })
  materialId!: number

  @Form({
    requiredNumber: true,
    number: true,
  })
  @Table({
    width: 150,
    money: true,
    order: -1,
  })
  @Field({
    label: '销售单价',
  })
  @KForm({ requiredNumber: true, number: true, precision: 2 })
  @KTable({ align: 'right', width: 150, order: -1, money: true })
  @KField({ label: '销售单价' })
  price!: number

  @Form({
    requiredNumber: true,
    number: true,
  })
  @Table({
    align: 'right',
    width: 150,
    order: -2,
  })
  @Field({
    label: '销售数量',
  })
  @KForm({ requiredNumber: true, number: true })
  @KTable({ align: 'right', width: 150, order: -2 })
  @KField({ label: '销售数量' })
  quantity!: number

  @Field()
  @Form({
    number: true,
  })
  @Table({
    align: 'right',
    width: 150,
    order: -3,
  })
  @KTable({ align: 'right', width: 150, order: -3 })
  @KField({ label: '已出库数量' })
  finishQuantity!: number

  @Type(CustomerEntity)
  @KType(CustomerEntity)
  @KField({ label: '销售客户' })
  customer!: CustomerEntity
}
