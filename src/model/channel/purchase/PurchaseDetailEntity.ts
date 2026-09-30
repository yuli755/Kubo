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
@KModel({
  label: '采购明细',
})
export class PurchaseDetailEntity extends AbstractBaseBillDetailEntity {
  @KForm({ requiredPayload: true })
  @KType(MaterialEntity)
  @KField({ label: '物料' })
  material!: MaterialEntity

  @KForm({ requiredPayload: true })
  @KType(SupplierEntity)
  @KField({ label: '供应商' })
  supplier!: SupplierEntity

  /*
   * 列宽按「列宽口径」（`src/framework/types.ts`）收过一轮，守卫 `tableLayout.spec.ts` 钉住 Σ ≤ 900。
   *
   * 改前 7 列全声明 140~180，Σ=1090，明细抽屉实测 Σ 列宽 1306 vs 可用 1120 ⇒ 横向溢出 186px，
   * 底部一条滚动条把「采购单价/数量/已采购数量」推到视口外（这三个才是明细行最该一眼看到的数）。
   * 实测内容宽（`getClientRects` 量单元格文本）：物料编码 75、物料名称 138、供应商名称 70、
   * 供应商编码 75、单价 35~70、数量 23。改后 Σ=860，只有「物料名称」是弹性列。
   */
  @KTable({ order: 90, width: 120, force: true, copy: true, formatter: (row: never) => materialPart(row, 'code') })
  @KField({ label: '物料编码' })
  materialCode!: string

  @KTable({ order: 80, minWidth: 160, force: true, copy: true, formatter: (row: never) => materialPart(row, 'name') })
  @KField({ label: '物料名称' })
  materialName!: string

  @KTable({ order: 70, width: 140, copy: true, formatter: (row: never) => supplierPart(row, 'name') })
  @KField({ label: '供应商名称' })
  supplierName!: string

  @KTable({ order: 60, width: 120, copy: true, formatter: (row: never) => supplierPart(row, 'code') })
  @KField({ label: '供应商编码' })
  supplierCode!: string

  @KForm({ requiredNumber: true, number: true, precision: 2 })
  @KTable({ align: 'right', width: 110, order: -1, money: true })
  @KField({ label: '采购单价' })
  price!: number

  @KForm({ requiredNumber: true, number: true })
  @KTable({ align: 'right', width: 100, order: -2 })
  @KField({ label: '采购数量' })
  quantity!: number

  @KTable({ align: 'right', width: 110, order: -3 })
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
