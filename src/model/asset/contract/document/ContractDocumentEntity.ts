import type { IFile } from '@airpower/web'
import { Field, Model, Table } from '@airpower/web'
import { BaseEntity } from '@/base/BaseEntity'
import { Field as KField, Model as KModel, Table as KTable } from '@/framework'

@Model({
  label: '合同附件',
})
@KModel({
  label: '合同附件',
})
export class ContractDocumentEntity extends BaseEntity implements IFile {
  @KTable({ order: 100, minWidth: 180 })
  @KField({ label: '附件名称' })
  @Field({
    label: '附件名称',
  })
  @Table()
  name!: string

  @KTable({ order: 90, minWidth: 220 })
  @KField({ label: '附件地址' })
  @Field({
    label: '附件地址',
  })
  @Table()
  url!: string

  @KTable({ order: 80, width: 120 })
  @KField({ label: '附件大小' })
  @Field({
    label: '附件大小',
  })
  @Table()
  size!: number
}
