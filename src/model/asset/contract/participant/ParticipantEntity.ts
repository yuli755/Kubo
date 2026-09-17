import { BaseEntity } from '@/base/BaseEntity'
import { DesensitizeType as KDesensitizeType, Field as KField, Form as KForm, Model as KModel, Search as KSearch, Table as KTable } from '@/framework'
import { CertificateTypeEnum } from '@/model/asset/contract/participant/CertificateTypeEnum'

@KModel({
  label: '参与方',
})
export class ParticipantEntity extends BaseEntity {
  role!: number

  @KTable({ order: 90, minWidth: 140, force: true })
  @KForm({ requiredString: true })
  @KField({ label: '名称' })
  @KSearch()
  name!: string

  @KTable({ order: 80, minWidth: 140, phone: true, desensitize: KDesensitizeType.MOBILE })
  @KForm({ requiredString: true, mobilePhone: true })
  @KField({ label: '联系电话' })
  @KSearch()
  phone!: string

  @KTable({ order: 70, minWidth: 160, hide: true, desensitize: KDesensitizeType.EMAIL })
  @KForm({ requiredString: true, email: true })
  @KField({ label: '邮箱' })
  email!: string

  @KTable({ order: 60, minWidth: 160, copy: true })
  @KForm({ requiredString: true })
  @KField({ label: '证件号' })
  @KSearch()
  identification!: string

  type!: number

  @KTable({ order: 40, width: 130, force: true, color: true })
  @KForm({ clearable: false, defaultValue: CertificateTypeEnum.CHINESE_ID_CARD.key, requiredNumber: true })
  @KField({ label: '证件类型', dictionary: CertificateTypeEnum })
  @KSearch({ width: 140 })
  certificateType!: number
}
