import { BaseEntity } from '@/base/BaseEntity'
import { Field as KField, Form as KForm, Model as KModel, Table as KTable, Type as KType } from '@/framework'
import { MaterialEntity } from '@/model/asset/material/MaterialEntity'
import { CustomerEntity } from '../customer/CustomerEntity'

/**
 * # 销售价（F4 批 5 起双注册）
 *
 * ⚠️ 迁移时修掉的一处旧错位：airpower 那两个扁平字段的**名字与标题是反的** ——
 * `customerName` 上挂的是「客户编码」、值取 `customer.code`，`customerCode` 上挂的是
 * 「客户名称」、值取 `customer.name`。K 侧按字段名重新配对（`customerCode` → 编码、
 * `customerName` → 名称），列表标题与列值这才对得上；旧装饰器保持原样不动（F5 再删）。
 */
@KModel({
  label: '销售价',
})
export class SalePriceEntity extends BaseEntity {
  @KForm({ requiredPayload: true })
  @KType(MaterialEntity)
  @KField({ label: '物料' })
  material!: MaterialEntity

  @KForm({ requiredPayload: true })
  @KType(CustomerEntity)
  @KField({ label: '客户' })
  customer!: CustomerEntity

  @KTable({ order: 92, minWidth: 140, force: true, copy: true, formatter: (row: never) => materialPart(row, 'code') })
  @KField({ label: '物料编码' })
  materialCode!: string

  @KTable({ order: 90, minWidth: 180, force: true, copy: true, formatter: (row: never) => materialPart(row, 'name') })
  @KField({ label: '物料名称' })
  materialName!: string

  @KTable({ order: 80, minWidth: 160, copy: true, formatter: (row: never) => customerPart(row, 'name') })
  @KField({ label: '客户名称' })
  customerName!: string

  @KTable({ order: 70, minWidth: 160, copy: true, formatter: (row: never) => customerPart(row, 'code') })
  @KField({ label: '客户编码' })
  customerCode!: string

  materialId!: number

  @KForm({ requiredNumber: true, number: true, precision: 2 })
  @KTable({ align: 'right', width: 150, order: -1, money: true, force: true })
  @KField({ label: '销售单价' })
  price!: number
}

/** 物料是嵌套对象（列表列渲染器读 `row.material.*`） */
function materialPart(row: unknown, part: 'code' | 'name'): string {
  const material = (row as { material?: Record<string, string> }).material
  return material?.[part] ?? ''
}

/** 客户是嵌套对象 */
function customerPart(row: unknown, part: 'code' | 'name'): string {
  const customer = (row as { customer?: Record<string, string> }).customer
  return customer?.[part] ?? ''
}
