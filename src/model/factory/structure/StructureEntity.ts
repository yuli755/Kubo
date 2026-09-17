import type { IPayload, ITree } from '@/framework'
// ⚠️ 过渡态双注册：见 StorageEntity 的说明（airpower 注册表留给存量选择器，框架注册表给新底座页面）。
import { BaseEntity } from '@/base/BaseEntity'
import { Field as KField, Form as KForm, Model as KModel, Search as KSearch, Table as KTable, Type as KType } from '@/framework'
import { StructureTypeEnum } from '@/model/factory/structure/StructureTypeEnum'
import { DepartmentEntity } from '@/model/personnel/department/DepartmentEntity'

@KModel({
  label: '生产单元',
})
export class StructureEntity extends BaseEntity implements ITree, IPayload {
  @KTable({ order: 100, minWidth: 160, copy: true })
  @KForm({ requiredString: true })
  @KField({ label: '生产单元名称' })
  @KSearch()
  name!: string

  @KTable({ order: 90, minWidth: 220, copy: true })
  @KForm({ placeholder: '不输入按编码规则自动生成', maxLength: 32 })
  @KField({ label: '生产单元编码' })
  @KSearch()
  code!: string

  @KTable({ order: 80, minWidth: 140, color: true })
  @KForm({ requiredNumber: true, defaultValue: StructureTypeEnum.SINGLE_STATION.key })
  @KSearch()
  @KField({ label: '生产单元类型', dictionary: StructureTypeEnum })
  type!: number

  @KField({ label: '子级' })
  @KType(StructureEntity)
  children!: this[]

  parentId!: number

  @KField({ label: '所属上级' })
  @KType(StructureEntity)
  parent!: this

  @KField({ label: '所属部门' })
  @KType(DepartmentEntity)
  departmentList!: DepartmentEntity[]

  getPayloadLabel(): string {
    return this.name
  }
}
