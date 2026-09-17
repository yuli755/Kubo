import type { IPayload } from '@/framework'
import { BaseEntity } from '@/base/BaseEntity'
// ⚠️ 过渡态双注册：同一个字段同时打 airpower 与本仓 `@/framework` 的装饰器。
// 原因：`MaterialEntity.unit` 这类**跨域嵌套字段**仍会被存量 airpower 页面/元数据注册表读到，
// 而新底座页面（KTable/KFormDialog）读 `@/framework` 的注册表 —— 两边都得看见列与表单配置。
// 注意：不要再拿 `UnitSelector` 当理由（该组件已在批 2 删除）；真正的原因是嵌套引用，
// 等 F5 删掉 airpower 依赖时，上面的 airpower 装饰器 + `BaseEntity` 基类一并换掉。
import {
  Field as KField,
  Form as KForm,
  Model as KModel,
  Search as KSearch,
  Table as KTable,
} from '@/framework'

@KModel({
  label: '计量单位',
})
export class UnitEntity extends BaseEntity implements IPayload {
  /**
   * ### 单位名称
   */
  @KTable({ order: 100, minWidth: 160, sortable: true })
  @KForm({ requiredString: true })
  @KField({ label: '单位名称' })
  @KSearch()
  name!: string

  /**
   * ### 单位编码
   */
  @KTable({ order: 90, minWidth: 160, sortable: true })
  @KForm({ placeholder: '不输入按编码规则自动生成', maxLength: 32 })
  @KField({ label: '单位编码' })
  @KSearch()
  code!: string

  getPayloadLabel(): string {
    return `${this.name} (${this.code})`
  }
}
