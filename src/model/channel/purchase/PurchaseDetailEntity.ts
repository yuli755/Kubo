import { Field, Form, Model, Table, Type } from '@airpower/web'
import { AbstractBaseBillDetailEntity } from '@/base/bill/detail/AbstractBaseBillDetailEntity'
import {
  Field as KField,
  Form as KForm,
  Model as KModel,
  Table as KTable,
  Type as KType,
} from '@/framework'
import { MaterialEntity } from '@/model/asset/material/MaterialEntity'
import { SupplierEntity } from '../supplier/SupplierEntity'

/**
 * # 采购明细（F4 批 5 起双注册）
 *
 * 嵌套对象在 K 侧必须带 `@KType`：`fill()` 靠它把 `material` / `supplier` 实例化成实体，
 * 否则明细表格列上的 `row.material.name` 这类 formatter 会在运行时炸。
 */
@Model({
  label: '采购明细',
})
@KModel({
  label: '采购明细',
})
export class PurchaseDetailEntity extends AbstractBaseBillDetailEntity {
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
  @Field({})
  @Type(SupplierEntity)
  @KForm({ requiredPayload: true })
  @KType(SupplierEntity)
  @KField({ label: '供应商' })
  supplier!: SupplierEntity

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

  @Table({
    formatter: row => row.supplier?.name || '-',
    copy: true,
  })
  @Field({
    label: '供应商名称',
  })
  @KTable({ order: 70, minWidth: 180, copy: true, formatter: (row: never) => supplierPart(row, 'name') })
  @KField({ label: '供应商名称' })
  supplierName!: string

  @Table({
    formatter: row => row.supplier?.code || '-',
    copy: true,
  })
  @Field({
    label: '供应商编码',
  })
  @KTable({ order: 60, minWidth: 140, copy: true, formatter: (row: never) => supplierPart(row, 'code') })
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
  @KTable({ align: 'right', width: 150, order: -1, money: true })
  @KField({ label: '采购单价' })
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
    label: '采购数量',
  })
  @KForm({ requiredNumber: true, number: true })
  @KTable({ align: 'right', width: 150, order: -2 })
  @KField({ label: '采购数量' })
  quantity!: number

  @Table({
    align: 'right',
    width: 150,
    order: -3,
  })
  @Field({
    label: '已采购数量',
  })
  @KTable({ align: 'right', width: 150, order: -3 })
  @KField({ label: '已采购数量' })
  finishQuantity!: number
}

/** 明细里的物料是嵌套对象（列表列渲染器读 `row.material.*`） */
function materialPart(row: unknown, part: 'code' | 'name'): string {
  const material = (row as { material?: Record<string, string> }).material
  return material?.[part] ?? ''
}

/** 明细里的供应商是嵌套对象（可空） */
function supplierPart(row: unknown, part: 'code' | 'name'): string {
  const supplier = (row as { supplier?: Record<string, string> }).supplier
  return supplier?.[part] ?? '-'
}
