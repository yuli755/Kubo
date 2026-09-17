import { Field, Form, Model, Table, Type } from '@airpower/web'
import { AbstractBaseBillDetailEntity } from '@/base/bill/detail/AbstractBaseBillDetailEntity'
// 双注册：明细的 K 表在单据编辑器/详情里渲染（`KDetailTable`），
// 与母单据同理，基类暂时仍是 airpower 的 `AbstractBaseBillDetailEntity`。
import {
  Field as KField,
  Form as KForm,
  Model as KModel,
  Table as KTable,
  Type as KType,
} from '@/framework'
import { InventoryEntity } from '../inventory/InventoryEntity'

@Model({
  label: '移库明细',
})
@KModel({
  label: '移库明细',
})
export class MoveDetailEntity extends AbstractBaseBillDetailEntity {
  @Type(InventoryEntity)
  @KType(InventoryEntity)
  @KField({ label: '来源库存' })
  inventory!: InventoryEntity

  @Table({
    force: true,
    formatter: row => row.inventory.material.code,
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
    formatter: row => row.inventory.material.name,
    copy: true,
  })
  @KTable({ force: true, minWidth: 180, order: 80, copy: true, formatter: (row: never) => materialPart(row, 'name') })
  @KField({ label: '物料名称' })
  @Field({
    label: '物料名称',
  })
  materialName!: string

  @Table({
    formatter: row => `${row.inventory.storage.name}(${row.inventory.storage.code})`,
  })
  @KTable({
    minWidth: 200,
    order: 70,
    formatter: (row: never) => {
      const storage = (row as { inventory?: { storage?: { name?: string, code?: string } } }).inventory?.storage
      return storage ? `${storage.name}(${storage.code})` : '-'
    },
  })
  @KField({ label: '来源仓库' })
  @Field({
    label: '来源仓库',
  })
  storageName!: string

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
  @KField({ label: '移动数量' })
  @Field({
    label: '移动数量',
  })
  quantity!: number

  @Table({
    align: 'right',
    width: 150,
    order: -3,
  })
  @KTable({ align: 'right', width: 150, order: -3 })
  @KField({ label: '已移动数量' })
  @Field({
    label: '已移动数量',
  })
  finishQuantity!: number
}

/** 从明细的嵌套库存里取物料字段（airpower 列的 formatter 与 K 列共用同一取值口径） */
function materialPart(row: unknown, part: 'name' | 'code'): string {
  const material = (row as { inventory?: { material?: Record<string, string> } }).inventory?.material
  return material?.[part] ?? ''
}
