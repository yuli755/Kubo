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

@KModel({
  label: '库存',
})
export class InventoryEntity extends BaseEntity {
  /**
   * 列宽口径见 `src/framework/types.ts`。本页是**双栏布局**（左侧库存类型树 + 右侧表格），
   * 表格可用宽只有 856px 而不是 1142px，改前 Σ 声明宽 946 ⇒ 横向溢出 90px；
   * 若切到「生产单元」库存类型（`list.vue:74` 的 `exclude-columns` 换成排除 `storageName`），
   * 溢出会到 290px。改后 Σ = 770 ≤ 800（856 - 56 序号），两种类型下都不再溢出。
   *
   * 实测内容宽（6 行取样）：物料编码 75、物料名称 138、仓库 117、库存类型 42、库存数量 23、计量单位 14，
   * 而声明宽是 140/180/200/120/150/100 ⇒ 除了物料名称，每列都富余一倍以上。
   */
  @KTable({ force: true, width: 120, order: 100, formatter: (row: never) => (row as { material?: { code?: string } }).material?.code ?? '' })
  @KField({ label: '物料编码' })
  materialCode!: string

  @KTable({ force: true, minWidth: 160, order: 90, formatter: (row: never) => (row as { material?: { name?: string } }).material?.name ?? '' })
  @KField({ label: '物料名称' })
  materialName!: string

  @KTable({ align: 'right', width: 110, order: -2, force: true })
  @KField({ label: '库存数量' })
  quantity!: number

  @KTable({ width: 90, order: -3, formatter: (row: never) => (row as { material?: { unit?: { name?: string } } }).material?.unit?.name ?? '' })
  @KField({ label: '计量单位' })
  unitName!: string

  @KType(StorageEntity)
  storage!: StorageEntity

  @KTable({ width: 180, order: 70, formatter: (row: never) => relationText((row as { storage?: { name?: string, code?: string } }).storage) })
  @KField({ label: '仓库' })
  storageName!: string

  @KTable({ width: 180, order: 60, formatter: (row: never) => relationText((row as { structure?: { name?: string, code?: string } }).structure) })
  @KField({ label: '生产单元' })
  structureName!: string

  @KTable({ width: 110, order: 50, color: true, formatter: (row: never) => getEnumLabel(InventoryTypeEnum, (row as { type?: number }).type) ?? '-' })
  @KField({ label: '库存类型', dictionary: InventoryTypeEnum })
  type!: number

  @KType(MaterialEntity)
  material!: MaterialEntity

  @KType(StructureEntity)
  structure!: StructureEntity
}
