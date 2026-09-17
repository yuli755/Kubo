import type { IPayload } from '@airpower/web'
// ⚠️ 过渡态双注册：同一个字段同时打 airpower 与本仓 `@/framework` 的装饰器。
// 存量 `MaterialSelector`（wms 的入库/出库、channel 的采购/销售单还在用，airpower ASelector）
// 读 airpower 注册表；新底座页面（KTable/KFormDialog，F4 批 2 起）读 `@/framework` 注册表。
// F4 迁完 wms / channel 域、删掉 `MaterialSelector` 后，删掉上面的 airpower 装饰器即可。
import { Field, Form, Model, Search, Table, Type } from '@airpower/web'
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

@Model({
  label: '物料',
})
@KModel({
  label: '物料',
})
export class MaterialEntity extends BaseEntity implements IPayload {
  @Table({
    copy: true,
    force: true,
  })
  @Form({
    requiredString: true,
  })
  @Search()
  @Field({
    label: '物料名称',
  })
  @KTable({ order: 100, minWidth: 160, force: true, copy: true })
  @KForm({ requiredString: true })
  @KField({ label: '物料名称' })
  @KSearch()
  name!: string

  @Table({
    copy: true,
    force: true,
  })
  @Search()
  @Form({
    placeholder: '不输入按编码规则自动生成',
  })
  @Field({
    label: '物料编码',
  })
  @KTable({ order: 90, minWidth: 160, force: true, copy: true })
  @KForm({ placeholder: '不输入按编码规则自动生成', maxLength: 32 })
  @KField({ label: '物料编码' })
  @KSearch()
  code!: string

  @Table({
    color: true,
    width: 100,
  })
  @Form({
    clearable: false,
    defaultValue: MaterialTypeEnum.PRODUCT.key,
    requiredNumber: true,
  })
  @Search({
    width: 120,
  })
  @KTable({ order: 80, width: 110, color: true })
  @KForm({ clearable: false, defaultValue: MaterialTypeEnum.PRODUCT.key, requiredNumber: true })
  @KField({ label: '物料类型', dictionary: MaterialTypeEnum })
  @KSearch({ width: 130 })
  @Field({
    label: '物料类型',
    dictionary: MaterialTypeEnum,
  })
  materialType!: number

  @Table({
    color: true,
    width: 100,
  })
  @Form({
    clearable: false,
    defaultValue: MaterialUseTypeEnum.CONSUMABLE.key,
    requiredNumber: true,
  })
  @Search({
    width: 120,
  })
  @KTable({ order: 70, width: 110, color: true })
  @KForm({ clearable: false, defaultValue: MaterialUseTypeEnum.CONSUMABLE.key, requiredNumber: true })
  @KField({ label: '使用类型', dictionary: MaterialUseTypeEnum })
  @KSearch({ width: 130 })
  @Field({
    label: '使用类型',
    dictionary: MaterialUseTypeEnum,
  })
  useType!: number

  @Table({
    copy: true,
  })
  @Form()
  @KTable({ order: 60, minWidth: 140 })
  @KForm({ maxLength: 64 })
  @KField({ label: '规格型号' })
  @Field({
    label: '规格型号',
  })
  spc!: string

  @Table({
    payload: true,
    width: 150,
  })
  @Form({
    requiredPayload: true,
  })
  @Field({
    label: '计量单位',
  })
  @Type(UnitEntity)
  @KTable({ order: 50, width: 120, payload: true })
  @KForm({ requiredPayload: true })
  @KField({ label: '计量单位' })
  @KType(UnitEntity)
  unit!: UnitEntity

  @Field({ label: '计量单位' })
  @Search()
  @KSearch({ label: '计量单位', placeholder: '按单位名称搜索' })
  @KField({ label: '计量单位名称' })
  unitName?: string

  @Table({
    width: 150,
    money: true,
    hide: true,
  })
  @Form({
    number: true,
    defaultValue: 0,
    requiredNumber: true,
  })
  @KTable({ order: 40, width: 140, money: true, hide: true })
  @KForm({ number: true, defaultValue: 0, requiredNumber: true, precision: 2 })
  @KField({ label: '采购单价' })
  @Field({
    label: '采购单价',
  })
  purchasePrice!: number

  @Table({
    width: 150,
    money: true,
    hide: true,
  })
  @Form({
    number: true,
    defaultValue: 0,
    requiredNumber: true,
  })
  @KTable({ order: 30, width: 140, money: true, hide: true })
  @KForm({ number: true, defaultValue: 0, requiredNumber: true, precision: 2 })
  @KField({ label: '销售单价' })
  @Field({
    label: '销售单价',
  })
  salePrice!: number

  getPayloadLabel(): string {
    return `${this.name}(${this.code})`
  }
}
