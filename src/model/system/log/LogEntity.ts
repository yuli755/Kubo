import { BaseEntity } from '@/base/BaseEntity'
import {
  DateTimeFormatter,
  Field as KField,
  Model as KModel,
  Table as KTable,
} from '@/framework'

@KModel({
  label: '日志',
})
export class LogEntity extends BaseEntity {
  @KTable()
  @KTable({
    order: 66,
    force: true,
  })
  @KField({
    label: '动作',
  })
  action!: string

  @KTable({
    width: 100,
  })
  @KField({
    label: '版本号',
  })
  version!: number

  @KTable()
  @KField({
    label: '平台',
  })
  platform!: string

  @KTable({
    order: 77,
    width: 160,
  })
  @KField({
    label: 'IP',
  })
  ip!: string

  @KField({
    label: '请求体',
  })
  request!: string

  @KField({
    label: '响应数据',
  })
  response!: string

  @KTable({
    width: 60,
  })
  @KField({
    label: '用户ID',
  })
  userId!: number

  @KField({
    label: '发起时间',
  })
  @KTable({
    hide: false,
    datetime: DateTimeFormatter.FULL_DATE_TIME,
    width: 150,
    order: 99,
    force: true,
  })
  declare createTime: number

  @KField({
    label: '响应时间',
  })
  @KTable({
    hide: false,
    order: 88,
    width: 150,
    force: true,
  })
  declare updateTime: number

  @KTable({
    width: 120,
    order: -1,
    align: 'right',
  })
  @KField({
    label: '总耗时',
  })
  pendingTime!: number
}
