import type { IPayload } from '@airpower/web'
// ⚠️ 过渡态双注册（同物料域）：存量合同编辑器/详情页读 airpower 注册表，
// 新底座页面（F4 批 2 的列表与详情）读 `@/framework` 注册表。
// 合同编辑器仍用 airpower（卡在上传组件），等批 2B 换完再删上面的 airpower 装饰器。
import { DateTimeFormatter, DateTimeType, DateTimeUtil, Field, Form, Model, Search, Table, Type } from '@airpower/web'
import { BaseEntity } from '@/base/BaseEntity'
import {
  DateTimeFormatter as KDateTimeFormatter,
  DateTimeType as KDateTimeType,
  DateTimeUtil as KDateTimeUtil,
  Field as KField,
  Form as KForm,
  Model as KModel,
  Search as KSearch,
  Table as KTable,
  Type as KType,
} from '@/framework'
import { ContractStatusEnum } from '@/model/asset/contract/ContractStatusEnum'
import { ContractDocumentEntity } from '@/model/asset/contract/document/ContractDocumentEntity'
import { ParticipantEntity } from '@/model/asset/contract/participant/ParticipantEntity'

import { ContractTypeEnum } from './ContractTypeEnum'

@Model({
  label: '合同',
})
@KModel({
  label: '合同',
})
export class ContractEntity extends BaseEntity implements IPayload {
  @Table({
    copy: true,
    force: true,
    order: 200,
    width: 200,
  })
  @Search()
  @Form({
    placeholder: '不输入按编码规则自动生成',
  })
  @KTable({ order: 200, width: 200, force: true, copy: true })
  @KForm({ placeholder: '不输入按编码规则自动生成', maxLength: 32 })
  @KField({ label: '合同编码' })
  @KSearch()
  @Field({
    label: '合同编码',
  })
  code!: string

  @Table({
    color: true,
    width: 100,
    order: 160,
  })
  @Form({
    clearable: false,
    defaultValue: ContractTypeEnum.OTHER.key,
    requiredNumber: true,
  })
  @Search({
    width: 120,
  })
  @KTable({ order: 160, width: 110, color: true })
  @KForm({ clearable: false, defaultValue: ContractTypeEnum.OTHER.key, requiredNumber: true })
  @KField({ label: '合同类型', dictionary: ContractTypeEnum })
  @KSearch({ width: 130 })
  @Field({
    label: '合同类型',
    dictionary: ContractTypeEnum,
  })
  type!: number

  @Table({
    force: true,
    order: 150,
  })
  @Form({
    requiredString: true,
  })
  @Search()
  @KTable({ order: 150, minWidth: 180, force: true })
  @KForm({ requiredString: true })
  @KField({ label: '合同名称' })
  @KSearch()
  @Field({
    label: '合同名称',
  })
  name!: string

  @Form({
    textarea: true,
    maxLength: 20000,
  })
  @KForm({ textarea: true, maxLength: 20000 })
  @KField({ label: '合同内容' })
  @Field({
    label: '合同内容',
  })
  content!: string

  @Table({
    width: 110,
    color: true,
    datetime: DateTimeFormatter.FULL_DATE,
  })
  @Form({
    defaultValue: DateTimeUtil.getMilliTimeStamps(),
    requiredNumber: true,
    dateType: DateTimeType.DATE,
    dateShowFormatter: DateTimeFormatter.FULL_DATE,
    dateFormatter: DateTimeFormatter.TIMESTAMP,
  })
  @KTable({ order: 130, width: 120, datetime: KDateTimeFormatter.FULL_DATE })
  @KForm({
    defaultValue: KDateTimeUtil.getMilliTimeStamps(),
    requiredNumber: true,
    dateType: KDateTimeType.DATE,
    dateShowFormatter: KDateTimeFormatter.FULL_DATE,
    dateFormatter: KDateTimeFormatter.TIMESTAMP,
  })
  @KField({ label: '开始时间' })
  @Field({
    label: '开始时间',
  })
  startTime!: number

  @Table({
    width: 110,
    color: true,
    datetime: DateTimeFormatter.FULL_DATE,
  })
  @Form({
    defaultValue: DateTimeUtil.getMilliTimeStamps() + DateTimeUtil.SECOND_PER_DAY * DateTimeUtil.DAY_PER_YEAR * DateTimeUtil.MILLISECONDS_PER_SECOND,
    requiredNumber: true,
    dateType: DateTimeType.DATE,
    dateShowFormatter: DateTimeFormatter.FULL_DATE,
    dateFormatter: DateTimeFormatter.TIMESTAMP,
  })
  @KTable({ order: 120, width: 120, datetime: KDateTimeFormatter.FULL_DATE })
  @KForm({
    defaultValue: KDateTimeUtil.getMilliTimeStamps() + KDateTimeUtil.SECOND_PER_DAY * KDateTimeUtil.DAY_PER_YEAR * KDateTimeUtil.MILLISECONDS_PER_SECOND,
    requiredNumber: true,
    dateType: KDateTimeType.DATE,
    dateShowFormatter: KDateTimeFormatter.FULL_DATE,
    dateFormatter: KDateTimeFormatter.TIMESTAMP,
  })
  @KField({ label: '终止时间' })
  @Field({
    label: '终止时间',
  })
  endTime!: number

  /**
   * # 合同状态
   */
  @Search({
    width: 120,
  })
  @Table({
    color: true,
    width: 100,
  })
  @KTable({ order: 140, width: 110, color: true })
  @KField({ label: '合同状态', dictionary: ContractStatusEnum })
  @KSearch({ width: 130 })
  @Field({
    label: '合同状态',
    dictionary: ContractStatusEnum,
  })
  status!: number

  @KField({ label: '参与方' })
  @KType(ParticipantEntity)
  @Field({
    label: '参与方',
  })
  @Type(ParticipantEntity, true)
  participantList: ParticipantEntity[] = []

  @KField({ label: '附件' })
  @KType(ContractDocumentEntity)
  @Field({
    label: '附件',
  })
  @Type(ContractDocumentEntity, true)
  documentList: ContractDocumentEntity[] = []

  getPayloadLabel(): string {
    return `${this.name}(${this.code})`
  }
}
