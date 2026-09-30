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
  @KTable({ order: 100, minWidth: 180, sortable: true })
  @KForm({ requiredString: true })
  @KField({ label: '单位名称' })
  @KSearch()
  name!: string

  /**
   * ### 单位编码
   *
   * 定宽 180（实测内容 90px）：编码长度有上界（`maxLength: 32`，实际是 `UT0001` 这种 6-8 位），
   * 给 `minWidth` 会让它跟着名称一起瓜分剩余宽度 —— 4 列的字典表里两列各被拉到 448px，
   * 格子里只有 84px 文字，其余全是空白。规范见 `src/framework/types.ts` 的「列宽口径」。
   */
  @KTable({ order: 90, width: 180, sortable: true })
  @KForm({ placeholder: '不输入按编码规则自动生成', maxLength: 32 })
  @KField({ label: '单位编码' })
  @KSearch()
  code!: string

  /**
   * 字典表（≤4 个业务列）必须带创建时间列：
   * 否则整张表只有「名称 + 编码」两列，剩余宽度全被它们吃掉，看着像排版坏了。
   * 基类只给了 `@Field`（列表列不进基类是刻意设计，见 `src/base/BaseEntity.ts` 的元数据口径），
   * 所以这里按 `RoleEntity.isDisabled` 的同一套写法重声明挂 `@KTable`。
   */
  @KTable({ order: -90, width: 170, datetime: true })
  @KField({ label: '创建时间' })
  declare createTime: number

  getPayloadLabel(): string {
    return `${this.name} (${this.code})`
  }
}
