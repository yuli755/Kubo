import type { IPayload } from '@/framework'
import { BaseEntity } from '@/base/BaseEntity'
// ⚠️ 过渡态双注册（同物料域）：存量合同编辑器/详情页读 airpower 注册表，
// 新底座页面（F4 批 2 的列表与详情）读 `@/framework` 注册表。
// 合同编辑器仍用 airpower（卡在上传组件），等批 2B 换完再删上面的 airpower 装饰器。
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

@KModel({
  label: '合同',
})
export class ContractEntity extends BaseEntity implements IPayload {
  @KTable({ order: 200, width: 200, force: true, copy: true })
  @KForm({ placeholder: '不输入按编码规则自动生成', maxLength: 32 })
  @KField({ label: '合同编码' })
  @KSearch()
  code!: string

  @KTable({ order: 160, width: 110, color: true })
  @KForm({ clearable: false, defaultValue: ContractTypeEnum.OTHER.key, requiredNumber: true })
  @KField({ label: '合同类型', dictionary: ContractTypeEnum })
  @KSearch({ width: 130 })
  type!: number

  @KTable({ order: 150, minWidth: 180, force: true })
  @KForm({ requiredString: true })
  @KField({ label: '合同名称' })
  @KSearch()
  name!: string

  @KForm({ textarea: true, maxLength: 20000 })
  @KField({ label: '合同内容' })
  content!: string

  @KTable({ order: 130, width: 120, datetime: KDateTimeFormatter.FULL_DATE })
  @KForm({
    defaultValue: KDateTimeUtil.getMilliTimeStamps(),
    requiredNumber: true,
    dateType: KDateTimeType.DATE,
    dateShowFormatter: KDateTimeFormatter.FULL_DATE,
    dateFormatter: KDateTimeFormatter.TIMESTAMP,
  })
  @KField({ label: '开始时间' })
  startTime!: number

  @KTable({ order: 120, width: 120, datetime: KDateTimeFormatter.FULL_DATE })
  @KForm({
    defaultValue: KDateTimeUtil.getMilliTimeStamps() + KDateTimeUtil.SECOND_PER_DAY * KDateTimeUtil.DAY_PER_YEAR * KDateTimeUtil.MILLISECONDS_PER_SECOND,
    requiredNumber: true,
    dateType: KDateTimeType.DATE,
    dateShowFormatter: KDateTimeFormatter.FULL_DATE,
    dateFormatter: KDateTimeFormatter.TIMESTAMP,
  })
  @KField({ label: '终止时间' })
  endTime!: number

  /**
   * # 合同状态
   */
  @KTable({ order: 140, width: 110, color: true })
  @KField({ label: '合同状态', dictionary: ContractStatusEnum })
  @KSearch({ width: 130 })
  status!: number

  @KField({ label: '参与方' })
  @KType(ParticipantEntity)
  participantList: ParticipantEntity[] = []

  @KField({ label: '附件' })
  @KType(ContractDocumentEntity)
  documentList: ContractDocumentEntity[] = []

  getPayloadLabel(): string {
    return `${this.name}(${this.code})`
  }
}
