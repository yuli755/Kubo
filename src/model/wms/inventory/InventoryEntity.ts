import { Field, Model, Table, Type } from '@airpower/web'
import { BaseEntity } from '@/base/BaseEntity'
// ⚠️ 过渡态双注册（口径见 `MaterialEntity`）：airpower 那套服务于还没迁的
// wms 入库/出库/移库编辑器与 airpower 选择器；`@K*` 服务于批 4 起的库存页与库存选择器。
import {
  getEnumLabel,
  Field as KField,
  Model as KModel,
  Table as KTable,
  Type as KType,
} from '@/framework'
import { MaterialEntity } from '@/model/asset/material/MaterialEntity'
import { StorageEntity } from '@/model/factory/storage/StorageEntity'
import { StructureEntity } from '@/model/factory/structure/StructureEntity'
import { InventoryTypeEnum } from '@/model/wms/inventory/InventoryTypeEnum'

/** 关联对象的展示口径：`名称(编码)`，与 airpower 列的 formatter 逐字一致（缺值给 `-`） */
function relationText(relation?: { name?: string, code?: string }): string {
  return `${relation?.name || '-'}(${relation?.code || '-'})`
}

@Model({
  label: '库存',
})
@KModel({
  label: '库存',
})
export class InventoryEntity extends BaseEntity {
  @Table({
    force: true,
    formatter: row => row.material.code,
  })
  @KTable({ force: true, minWidth: 140, order: 100, formatter: (row: never) => (row as { material?: { code?: string } }).material?.code ?? '' })
  @KField({ label: '物料编码' })
  @Field({
    label: '物料编码',
  })
  materialCode!: string

  @Table({
    force: true,
    formatter: row => row.material.name,
  })
  @KTable({ force: true, minWidth: 180, order: 90, formatter: (row: never) => (row as { material?: { name?: string } }).material?.name ?? '' })
  @KField({ label: '物料名称' })
  @Field({
    label: '物料名称',
  })
  materialName!: string

  @Table({
    align: 'right',
    width: 150,
    order: -2,
    force: true,
  })
  @KTable({ align: 'right', width: 150, order: -2, force: true })
  @KField({ label: '库存数量' })
  @Field({
    label: '库存数量',
  })
  quantity!: number

  @Table({
    width: 100,
    order: -3,
    formatter: row => row.material.unit.name,
  })
  @KTable({ width: 100, order: -3, formatter: (row: never) => (row as { material?: { unit?: { name?: string } } }).material?.unit?.name ?? '' })
  @KField({ label: '计量单位' })
  @Field({
    label: '计量单位',
  })
  unitName!: string

  @Field({
    label: '仓库',
  })
  @Type(StorageEntity)
  @KType(StorageEntity)
  storage!: StorageEntity

  @Table({
    formatter: row => `${row.storage?.name || '-'}(${row.storage?.code || '-'})`,
  })
  @KTable({ minWidth: 200, order: 70, formatter: (row: never) => relationText((row as { storage?: { name?: string, code?: string } }).storage) })
  @KField({ label: '仓库' })
  @Field({
    label: '仓库',
  })
  storageName!: string

  @Table({
    formatter: row => `${row.structure?.name || '-'}(${row.structure?.code || '-'})`,
  })
  @KTable({ minWidth: 200, order: 60, formatter: (row: never) => relationText((row as { structure?: { name?: string, code?: string } }).structure) })
  @KField({ label: '生产单元' })
  @Field({
    label: '生产单元',
  })
  structureName!: string

  @KTable({ width: 120, order: 50, color: true, formatter: (row: never) => getEnumLabel(InventoryTypeEnum, (row as { type?: number }).type) ?? '-' })
  @KField({ label: '库存类型', dictionary: InventoryTypeEnum })
  @Field({
    label: '库存类型',
    dictionary: InventoryTypeEnum,
  })
  type!: number

  @Field({
    label: '物料信息',
  })
  @Type(MaterialEntity)
  @KType(MaterialEntity)
  material!: MaterialEntity

  @Field({
    label: '生产单元',
  })
  @Type(StructureEntity)
  @KType(StructureEntity)
  structure!: StructureEntity
}
