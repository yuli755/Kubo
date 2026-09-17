import type { IFile } from '@/framework'
import { BaseEntity } from '@/base/BaseEntity'
import { Field as KField, Model as KModel, Table as KTable } from '@/framework'

@KModel({
  label: '合同附件',
})
export class ContractDocumentEntity extends BaseEntity implements IFile {
  @KTable({ order: 100, minWidth: 180 })
  @KField({ label: '附件名称' })
  name!: string

  @KTable({ order: 90, minWidth: 220 })
  @KField({ label: '附件地址' })
  url!: string

  @KTable({ order: 80, width: 120 })
  @KField({ label: '附件大小' })
  size!: number
}
