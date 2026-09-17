import type { IPayload, ITree } from '@/framework'
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
