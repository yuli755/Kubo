import type { IPayload, ITree } from '@airpower/web'
import { Field, Form, Model, Search, Table, Type } from '@airpower/web'
// ⚠️ 过渡态双注册：同一个字段同时打 airpower 与本仓 `@/framework` 的装饰器。
// 存量 `StorageSelector`（wms 的入库/移库页还在用，airpower ASelector）读 airpower 注册表，
// 新底座页面（KTable/KFormDialog）读 `@/framework` 注册表 —— 两边都得看见列与表单配置。
// F4 迁完 wms 域、删掉 StorageSelector 后，删掉上面的 airpower 装饰器即可。
import { BaseEntity } from '@/base/BaseEntity'
import {
  Field as KField,
  Form as KForm,
  Model as KModel,
  Search as KSearch,
  Table as KTable,
  Type as KType,
} from '@/framework'

@Model({
  label: '仓库',
})
@KModel({
  label: '仓库',
})
export class StorageEntity extends BaseEntity implements ITree, IPayload {
  @Table({
    copy: true,
    force: true,
  })
  @Form({
    requiredString: true,
  })
  @Field({
    label: '仓库名称',
  })
  @Search()
  @KTable({ order: 100, minWidth: 160, force: true, copy: true })
  @KForm({ requiredString: true })
  @KField({ label: '仓库名称' })
  @KSearch()
  name!: string

  @Table({
    width: 300,
    copy: true,
    force: true,
  })
  @Form({
    placeholder: '不输入按编码规则自动生成',
  })
  @Field({
    label: '仓库编码',
  })
  @Search()
  @KTable({ order: 90, minWidth: 220, force: true, copy: true })
  @KForm({ placeholder: '不输入按编码规则自动生成', maxLength: 32 })
  @KField({ label: '仓库编码' })
  @KSearch()
  code!: string

  @Field({})
  @Type(StorageEntity, true)
  @KField({ label: '子级' })
  @KType(StorageEntity)
  children!: this[]

  parentId!: number

  @Field({
    label: '所属上级',
  })
  @Type(StorageEntity)
  @KField({ label: '所属上级' })
  @KType(StorageEntity)
  parent!: this

  getPayloadLabel(): string {
    return this.name
  }
}
