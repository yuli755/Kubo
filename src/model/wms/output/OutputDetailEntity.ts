import { Field, Form, Model, Table, Type } from '@airpower/web'
import { AbstractBaseBillDetailEntity } from '@/base/bill/detail/AbstractBaseBillDetailEntity'
// 双注册：明细的 K 表在单据编辑器/详情里渲染（`KDetailTable`），基类暂时仍是 airpower 的。
import {
  Field as KField,
  Form as KForm,
  Model as KModel,
  Table as KTable,
  Type as KType,
} from '@/framework'
import { MaterialEntity } from '@/model/asset/material/MaterialEntity'
import { InventoryEntity } from '../inventory/InventoryEntity'

@Model({
  label: '出库明细',
})
@KModel({
  label: '出库明细',
})
export class OutputDetailEntity extends AbstractBaseBillDetailEntity {
  @Type(InventoryEntity)
  @KType(InventoryEntity)
  @KField({ label: '库存来源' })
  inventory!: InventoryEntity

  @Type(MaterialEntity)
  @KType(MaterialEntity)
  @KField({ label: '物料' })
  @KForm({ requiredPayload: true })
  @Form({
    requiredPayload: true,
  })
  material!: MaterialEntity

  @Table({
    force: true,
    formatter: row => row.material.code,
    copy: true,
  })
  @KTable({ force: true, minWidth: 140, order: 90, copy: true, formatter: (row: never) => materialPart(row, 'code') })
  @KField({ label: '物料编码' })
  @Field({
    label: '物料编码',
  })
  materialCode!: string

  @Table({
    force: true,
    formatter: row => row.material.name,
    copy: true,
  })
  @KTable({ force: true, minWidth: 180, order: 80, copy: true, formatter: (row: never) => materialPart(row, 'name') })
  @KField({ label: '物料名称' })
  @Field({
    label: '物料名称',
  })
  materialName!: string

  @KTable({ minWidth: 180, order: 70, formatter: (row: never) => {
    const storage = (row as { inventory?: { storage?: { name?: string, code?: string } } }).inventory?.storage
    return storage ? `${storage.name}(${storage.code})` : '-'
  } })
  @KField({ label: '来源仓库' })
  @Field({
    label: '来源仓库',
  })
  @Table({
    formatter: row => `${row.inventory?.storage.name || '-'}(${row.inventory?.storage.code || '-'})`,
  })
  storageName!: string

  @KForm({ requiredNumber: true })
  @KField({ label: '物料信息' })
  @Form({
    requiredNumber: true,
  })
  @Field({
    label: '物料信息',
  })
  materialId!: number

  @Form({
    requiredNumber: true,
    number: true,
  })
  @Table({
    align: 'right',
    width: 150,
    order: -2,
    force: true,
  })
  @KTable({ align: 'right', width: 150, order: -2, force: true })
  @KForm({ number: true, requiredNumber: true })
  @KField({ label: '出库数量' })
  @Field({
    label: '出库数量',
  })
  quantity!: number

  @Table({
    align: 'right',
    width: 150,
    order: -3,
  })
  @KTable({ align: 'right', width: 150, order: -3 })
  @KForm({ number: true })
  @KField({ label: '已出库数量' })
  @Field({
    label: '已出库数量',
  })
  finishQuantity!: number

  @KForm({ requiredNumber: true })
  @KField({ label: '库存来源' })
  @Form({
    requiredNumber: true,
  })
  @Field({
    label: '库存来源',
  })
  inventoryId!: number
}

/** 从明细的嵌套物料里取字段（airpower 列与 K 列共用同一取值口径） */
function materialPart(row: unknown, part: 'name' | 'code'): string {
  const material = (row as { material?: Record<string, string> }).material
  return material?.[part] ?? ''
}
