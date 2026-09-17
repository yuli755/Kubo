import { BaseEntity } from '@/base/BaseEntity'
import { Field as KField } from '@/framework'

export class PersonalTokenEntity extends BaseEntity {
  @KField({
    label: '令牌名称',
  })
  name!: string

  @KField({
    label: '令牌',
  })
  token!: string
}
