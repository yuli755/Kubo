import type { IPayload, ITree } from '@/framework'
// ⚠️ 过渡态双注册：同一个字段同时打 airpower 与本仓 `@/framework` 的装饰器。
// 存量 `StorageSelector`（wms 的入库/移库页还在用，airpower ASelector）读 airpower 注册表，
// 新底座页面（KTable/KFormDialog）读 `@/framework` 注册表 —— 两边都得看见列与表单配置。
// F4 迁完 wms 域、删掉 StorageSelector 后，删掉上面的 airpower 装饰器即可。
import { BaseEntity } from '@/base/BaseEntity'
import { Field as KField, Form as KForm, Model as KModel, Search as KSearch, Table as KTable, Type as KType } from '@/framework'

@KModel({
  label: '仓库',
})
export class StorageEntity extends BaseEntity implements ITree, IPayload {
  @KTable({ order: 100, minWidth: 160, force: true, copy: true })
  @KForm({ requiredString: true })
  @KField({ label: '仓库名称' })
  @KSearch()
  name!: string

  @KTable({ order: 90, minWidth: 220, force: true, copy: true })
  @KForm({ placeholder: '不输入按编码规则自动生成', maxLength: 32 })
  @KField({ label: '仓库编码' })
  @KSearch()
  code!: string

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
