import { Field, Form, Model, Table, Type } from '@airpower/web'
import { BaseEntity } from '@/base/BaseEntity'
import { Field as KField, Form as KForm, Model as KModel, Table as KTable, Type as KType } from '@/framework'
import { MaterialEntity } from '@/model/asset/material/MaterialEntity'
import { SupplierEntity } from '../supplier/SupplierEntity'

/** 物料是嵌套对象（列表列渲染器读 `row.material.*`） */
function materialPart(row: unknown, part: 'code' | 'name'): string {
  const material = (row as { material?: Record<string, string> }).material
  return material?.[part] ?? ''
}

/** 供应商是嵌套对象 */
function supplierPart(row: unknown, part: 'code' | 'name'): string {
  const supplier = (row as { supplier?: Record<string, string> }).supplier
  return supplier?.[part] ?? ''
}

/**
 * # 采购价
 *
 * F4 批 5 起双注册：K 侧服务于采购价列表与编辑器。
 * 价格按「物料 + 供应商」唯一，两列都读嵌套对象，因此两个关联字段都要 `@KType`。
 */
@Model({
  label: '采购价',
})
@KModel({
  label: '采购价',
})
export class PurchasePriceEntity extends BaseEntity {
  @Form({
    requiredPayload: true,
  })
  @Type(MaterialEntity)
  @KForm({ requiredPayload: true })
  @KType(MaterialEntity)
  @KField({ label: '物料' })
  material!: MaterialEntity

  @Form({
    requiredPayload: true,
  })
  @Table()
  @Field({
    label: '供应商',
  })
  @Type(SupplierEntity)
  @KForm({ requiredPayload: true })
  @KType(SupplierEntity)
  @KTable({ order: 60, minWidth: 160, formatter: (row: never) => supplierPart(row, 'name') })
  @KField({ label: '供应商' })
  supplier!: SupplierEntity

  @Table({
    formatter: row => row.material.code,
    copy: true,
  })
  @Field({
    label: '物料编码',
  })
  @KTable({ order: 92, minWidth: 140, force: true, copy: true, formatter: (row: never) => materialPart(row, 'code') })
  @KField({ label: '物料编码' })
  materialCode!: string

  @Table({
    formatter: row => row.material.name,
    copy: true,
  })
  @Field({
    label: '物料名称',
  })
  @KTable({ order: 90, minWidth: 180, force: true, copy: true, formatter: (row: never) => materialPart(row, 'name') })
  @KField({ label: '物料名称' })
  materialName!: string

  @Table({
    formatter: row => row.supplier.name,
    copy: true,
  })
  @Field({
    label: '供应商名称',
  })
  @KTable({ order: 80, minWidth: 160, copy: true, formatter: (row: never) => supplierPart(row, 'name') })
  @KField({ label: '供应商名称' })
  supplierName!: string

  @Table({
    formatter: row => row.supplier.code,
    copy: true,
  })
  @Field({
    label: '供应商编码',
  })
  @KTable({ order: 70, minWidth: 140, copy: true, formatter: (row: never) => supplierPart(row, 'code') })
  @KField({ label: '供应商编码' })
  supplierCode!: string

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
    label: '采购单价',
  })
  @KForm({ requiredNumber: true, number: true, precision: 2 })
  @KTable({ align: 'right', width: 150, order: -1, money: true, force: true })
  @KField({ label: '采购单价' })
  price!: number
}
