import { BaseEntity } from '@/base/BaseEntity'
import {
  Field as KField,
  Form as KForm,
  Model as KModel,
  Search as KSearch,
  Table as KTable,
  Type as KType,
} from '@/framework'
import { MaterialUseTypeEnum } from '@/model/asset/material/MaterialUseTypeEnum'
import { UnitEntity } from '@/model/system/unit/UnitEntity'
import { MaterialTypeEnum } from './MaterialTypeEnum'

/**
 * # 物料实体
 *
 * F4 批 2 起单注册新底座（`@K*`）；批 5 删掉 airpower 装饰器的条件已满足：
 * 最后两个消费者 —— 采购/销售的价格页与 `MaterialSelector` —— 都换成了
 * `KSelector + KMaterialService`，域内已无页面读 airpower 注册表。
 *
 * 因此这里不再有 `@Model/@Table/@Form/@Search/@Field/@Type` 那套过渡双注册，
 * `@K*` 就是唯一一份元数据。
 */
@KModel({
  label: '物料',
})
export class MaterialEntity extends BaseEntity {
  @KTable({ order: 100, minWidth: 160, force: true, copy: true })
  @KForm({ requiredString: true })
  @KField({ label: '物料名称' })
  @KSearch()
  name!: string

  /**
   * ### 物料编码
   *
   * 定宽 180（实测 75px）。原 `minWidth: 160` 与名称、规格型号一起瓜分剩余宽度，
   * 实测编码 193 / 规格 169，内容只有 75 / 55px。列宽口径见 `src/framework/types.ts`。
   */
  @KTable({ order: 90, width: 180, force: true, copy: true })
  @KForm({ placeholder: '不输入按编码规则自动生成', maxLength: 32 })
  @KField({ label: '物料编码' })
  @KSearch()
  code!: string

  @KTable({ order: 80, width: 110, color: true })
  @KForm({ clearable: false, defaultValue: MaterialTypeEnum.PRODUCT.key, requiredNumber: true })
  @KField({ label: '物料类型', dictionary: MaterialTypeEnum })
  @KSearch({ width: 130 })
  materialType!: number

  @KTable({ order: 70, width: 110, color: true })
  @KForm({ clearable: false, defaultValue: MaterialUseTypeEnum.CONSUMABLE.key, requiredNumber: true })
  @KField({ label: '使用类型', dictionary: MaterialUseTypeEnum })
  @KSearch({ width: 130 })
  useType!: number

  @KTable({ order: 60, width: 140 })
  @KForm({ maxLength: 64 })
  @KField({ label: '规格型号' })
  spc!: string

  @KTable({ order: 50, width: 120, payload: true })
  @KForm({ requiredPayload: true })
  @KField({ label: '计量单位' })
  @KType(UnitEntity)
  unit!: UnitEntity

  @KSearch({ label: '计量单位', placeholder: '按单位名称搜索' })
  @KField({ label: '计量单位名称' })
  unitName?: string

  @KTable({ order: 40, width: 140, money: true, hide: true })
  @KForm({ number: true, defaultValue: 0, requiredNumber: true, precision: 2 })
  @KField({ label: '采购单价' })
  purchasePrice!: number

  @KTable({ order: 30, width: 140, money: true, hide: true })
  @KForm({ number: true, defaultValue: 0, requiredNumber: true, precision: 2 })
  @KField({ label: '销售单价' })
  salePrice!: number

  getPayloadLabel(): string {
    return `${this.name}(${this.code})`
  }
}
