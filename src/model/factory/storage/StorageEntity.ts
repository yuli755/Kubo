import type { IPayload, ITree } from '@/framework'
import { BaseEntity } from '@/base/BaseEntity'
import { Field as KField, Form as KForm, Model as KModel, Search as KSearch, Table as KTable, Type as KType } from '@/framework'

@KModel({
  label: '仓库',
})
export class StorageEntity extends BaseEntity implements ITree, IPayload {
  @KTable({ order: 100, minWidth: 180, force: true, copy: true })
  @KForm({ requiredString: true })
  @KField({ label: '仓库名称' })
  @KSearch()
  name!: string

  /**
   * ### 仓库编码
   *
   * 定宽 180（实测内容 52px）。原先是 `minWidth: 220`：树表只有两列业务字段，
   * 剩余宽度全被名称与编码瓜分，实测各撑到 361px / 495px，格子里 52px 文字 + 440px 空白。
   * 列宽口径见 `src/framework/types.ts`。
   */
  @KTable({ order: 90, width: 180, force: true, copy: true })
  @KForm({ placeholder: '不输入按编码规则自动生成', maxLength: 32 })
  @KField({ label: '仓库编码' })
  @KSearch()
  code!: string

  /**
   * 字典/基础数据表统一补「创建时间」列：把弹性列之外的空间交给有信息量的字段，
   * 而不是让两列被拉成空壳。基类只给 `@Field`（列表列不进基类，见 `src/base/BaseEntity.ts`），
   * 所以按 `RoleEntity.isDisabled` 的写法重声明挂 `@KTable`。
   */
  @KTable({ order: -90, width: 170, datetime: true })
  @KField({ label: '创建时间' })
  declare createTime: number

  @KField({ label: '子级' })
  @KType(StorageEntity)
  children!: this[]

  parentId!: number

  @KField({ label: '所属上级' })
  @KType(StorageEntity)
  parent!: this

  getPayloadLabel(): string {
    return this.name
  }
}
