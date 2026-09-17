import { DesensitizeType, Field, Form, Model, Search, Table } from '@airpower/web'
import { BaseEntity } from '@/base/BaseEntity'
import {
  DesensitizeType as KDesensitizeType,
  Field as KField,
  Form as KForm,
  Model as KModel,
  Search as KSearch,
  Table as KTable,
} from '@/framework'
import { CertificateTypeEnum } from '@/model/asset/contract/participant/CertificateTypeEnum'
import { ParticipantRoleEnum } from '@/model/asset/contract/participant/ParticipantRoleEnum'
import { ParticipantTypeEnum } from '@/model/asset/contract/participant/ParticipantTypeEnum'

@Model({
  label: '参与方',
})
@KModel({
  label: '参与方',
})
export class ParticipantEntity extends BaseEntity {
  @Search({
    width: 90,
  })
  @Table({
    color: true,
    order: 100,
    width: 80,
    force: true,
  })
  @Field({
    label: '身份',
    dictionary: ParticipantRoleEnum,
  })
  @Form({
    defaultValue: ParticipantRoleEnum.A.key,
    clearable: false,
  })
  role!: number

  @Table({
    force: true,
  })
  @Form({
    requiredString: true,
  })
  @Search()
  @KTable({ order: 90, minWidth: 140, force: true })
  @KForm({ requiredString: true })
  @KField({ label: '名称' })
  @KSearch()
  @Field({
    label: '名称',
  })
  name!: string

  @Table({
    phone: true,
    desensitize: DesensitizeType.MOBILE,
  })
  @Form({
    requiredString: true,
  })
  @Search()
  @KTable({ order: 80, minWidth: 140, phone: true, desensitize: KDesensitizeType.MOBILE })
  @KForm({ requiredString: true, mobilePhone: true })
  @KField({ label: '联系电话' })
  @KSearch()
  @Field({
    label: '联系电话',
  })
  phone!: string

  @Table({
    hide: true,
    desensitize: DesensitizeType.EMAIL,
  })
  @Form({
    requiredString: true,
  })
  @KTable({ order: 70, minWidth: 160, hide: true, desensitize: KDesensitizeType.EMAIL })
  @KForm({ requiredString: true, email: true })
  @KField({ label: '邮箱' })
  @Field({
    label: '邮箱',
  })
  email!: string

  @Table({
    copy: true,
  })
  @Form({
    requiredString: true,
  })
  @Search()
  @KTable({ order: 60, minWidth: 160, copy: true })
  @KForm({ requiredString: true })
  @KField({ label: '证件号' })
  @KSearch()
  @Field({
    label: '证件号',
  })
  identification!: string

  @Search({
    width: 120,
  })
  @Table({
    color: true,
    width: 80,
    force: true,
  })
  @Field({
    label: '身份类型',
    dictionary: ParticipantTypeEnum,
  })
  @Form({
    defaultValue: ParticipantTypeEnum.PERSONAL.key,
    clearable: false,
  })
  type!: number

  @Search({
    width: 130,
  })
  @Table({
    color: true,
    width: 120,
    force: true,
  })
  @Form({
    clearable: false,
    defaultValue: CertificateTypeEnum.CHINESE_ID_CARD.key,
  })
  @KTable({ order: 40, width: 130, force: true, color: true })
  @KForm({ clearable: false, defaultValue: CertificateTypeEnum.CHINESE_ID_CARD.key, requiredNumber: true })
  @KField({ label: '证件类型', dictionary: CertificateTypeEnum })
  @KSearch({ width: 140 })
  @Field({
    label: '证件类型',
    dictionary: CertificateTypeEnum,
  })
  certificateType!: number
}
