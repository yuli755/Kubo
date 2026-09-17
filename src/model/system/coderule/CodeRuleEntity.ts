import { BaseEntity } from '@/base/BaseEntity'
import {
  Field as KField,
  Form as KForm,
  Model as KModel,
  Search as KSearch,
  Table as KTable,
} from '@/framework'
import { SerialNumberUpdateEnum } from '@/model/system/coderule/SerialNumberUpdateEnum'

/**
 * # 编码规则实体
 *
 * @author Hamm.cn
 */
@KModel({
  permissionPrefix: 'CodeRule',
  label: '编码规则',
})
export class CodeRuleEntity extends BaseEntity {
  @KForm({
    requiredNumber: true,
  })
  @KTable({
    force: true,
  })
  @KField({
    label: '编码所属字段',
  })
  ruleField!: number

  @KForm({
    maxLength: 10,
    minLength: 1,
    requiredString: true,
    defaultValue: '',
  })
  @KTable({
    force: true,
  })
  @KField({
    label: '规则前缀',
  })
  prefix!: string

  @KForm({
    max: 10,
    min: 1,
    number: true,
    requiredNumber: true,
    defaultValue: 4,
  })
  @KTable({
    width: 160,
    force: true,
  })
  @KField({
    label: '序列号初始长度',
  })
  snLength!: number

  @KTable({
    force: true,
  })
  @KForm({
    defaultValue: '',
    clearable: true,
    maxLength: 64,
  })
  @KField({
    label: '规则模板',
  })
  @KSearch()
  template!: string

  @KForm({
    clearable: false,
    defaultValue: SerialNumberUpdateEnum.DAY.key,
    requiredNumber: true,
  })
  @KTable({
    color: true,
    width: 100,
    force: true,
  })
  @KField({
    label: '序列号更新',
    dictionary: SerialNumberUpdateEnum,
  })
  @KSearch({
    width: 130,
  })
  snType!: number

  @KTable()
  @KField({
    label: '下一个编码',
  })
  nextCode!: string

  currentSn!: number
}
