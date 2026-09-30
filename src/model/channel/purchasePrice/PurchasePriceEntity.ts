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
@KModel({
  label: '采购价',
})
export class PurchasePriceEntity extends BaseEntity {
  @KForm({ requiredPayload: true })
  @KType(MaterialEntity)
  @KField({ label: '物料' })
  material!: MaterialEntity

  /**
   * 供应商关联字段：**只给表单/详情用，不挂 `@KTable`**。
   *
   * 曾经挂着 `@KTable({ order: 60, formatter: supplierPart(row, 'name') })`，
   * 与下面专门的 `supplierName` 列渲染出**同一个字符串**，列表里并排出现「供应商 / 供应商名称」
   * 两列一模一样的内容（实测各 163px，内容 69 / 70px）。
   * 对照 `material` 的处理方式（同样不挂列，由 `materialCode` / `materialName` 两列展示），
   * 关联字段本身不进表格才是这套实体的一致写法。
   */
  @KForm({ requiredPayload: true })
  @KType(SupplierEntity)
  @KField({ label: '供应商' })
  supplier!: SupplierEntity

  @KTable({ order: 92, width: 170, force: true, copy: true, formatter: (row: never) => materialPart(row, 'code') })
  @KField({ label: '物料编码' })
  materialCode!: string

  @KTable({ order: 90, minWidth: 180, force: true, copy: true, formatter: (row: never) => materialPart(row, 'name') })
  @KField({ label: '物料名称' })
  materialName!: string

  @KTable({ order: 80, width: 170, copy: true, formatter: (row: never) => supplierPart(row, 'name') })
  @KField({ label: '供应商名称' })
  supplierName!: string

  @KTable({ order: 70, width: 170, copy: true, formatter: (row: never) => supplierPart(row, 'code') })
  @KField({ label: '供应商编码' })
  supplierCode!: string

  @KForm({ requiredNumber: true, number: true, precision: 2 })
  @KTable({ align: 'right', width: 140, order: -1, money: true, force: true })
  @KField({ label: '采购单价' })
  price!: number
}
