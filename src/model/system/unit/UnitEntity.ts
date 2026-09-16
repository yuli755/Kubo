import type { IPayload } from '@airpower/web'
// ⚠️ 过渡态双注册：同一个字段同时打 airpower 与本仓 `@/framework` 的装饰器。
// 原因：存量 `UnitSelector`（airpower ASelector）读 airpower 的元数据注册表，
// 新底座页面（KTable/KFormDialog）读 `@/framework` 的注册表 —— 两边都得看见列与表单配置。
// F4 按域迁移时删掉上面这行 airpower 装饰器即可（届时 UnitSelector 一并删除）。
import { Field, Form, Model, Search, Table } from '@airpower/web'
import { BaseEntity } from '@/base/BaseEntity'
import {
  Field as KField,
  Form as KForm,
  Model as KModel,
  Search as KSearch,
  Table as KTable,
} from '@/framework'

@Model({
  label: '计量单位',
})
@KModel({
  label: '计量单位',
})
export class UnitEntity extends BaseEntity implements IPayload {
  /**
   * ### 单位名称
   */
  @Table({
    force: true,
    copy: true,
  })
  @Form({
    requiredString: true,
  })
  @Field({
    label: '单位名称',
  })
  @Search()
  @KTable({ order: 100, minWidth: 160, sortable: true })
  @KForm({ requiredString: true })
  @KField({ label: '单位名称' })
  @KSearch()
  name!: string

  /**
   * ### 单位编码
   */
  @Table({
    copy: true,
    force: true,
  })
  @Form({
    placeholder: '不输入按编码规则自动生成',
  })
  @Field({
    label: '单位编码',
  })
  @Search()
  @KTable({ order: 90, minWidth: 160, sortable: true })
  @KForm({ placeholder: '不输入按编码规则自动生成', maxLength: 32 })
  @KField({ label: '单位编码' })
  @KSearch()
  code!: string

  getPayloadLabel(): string {
    return `${this.name} (${this.code})`
  }
}
