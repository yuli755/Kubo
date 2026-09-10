import { Field, Form, Model, Table } from '@airpower/web'
import { BaseEntity } from '@/base/BaseEntity'
import { CollectionGranularityEnum } from './CollectionGranularityEnum'

@Model({
  label: '采集参数',
})
export class CollectionEntity extends BaseEntity {
  @Table({
    force: true,
  })
  @Form({
    requiredString: true,
  })
  @Field({
    label: '参数名称',
  })
  code!: string

  @Table({
    force: true,
  })
  @Form({
    requiredString: true,
  })
  @Field({
    label: '参数标题',
  })
  label!: string

  @Table({
    force: true,
  })
  @Form({
    requiredString: true,
  })
  @Field({
    label: '参数采集值',
  })
  value!: string

  @Field({
    label: '最后变更',
  })
  timestamp!: number

  @Field({
    label: 'uuid',
  })
  uuid!: string

  /**
   * ### 采集端 API-Key
   * 历史查询出口（getDevicePayloadHistory）认证用，随请求提交、不回显。
   */
  apiKey!: string

  @Form()
  @Field({
    label: '颗粒度',
    dictionary: CollectionGranularityEnum,
  })
  reportGranularity!: number

  intValue!: number

  booleanValue!: boolean

  strValue!: string

  startTime!: number

  endTime!: number

  dataType!: number
}
