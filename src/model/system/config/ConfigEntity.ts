import { BaseEntity } from '@/base/BaseEntity'
import {
  Field as KField,
  Form as KForm,
  Model as KModel,
  Search as KSearch,
  Table as KTable,
} from '@/framework'
import { BooleanYesNoDictionary } from '@/model/common/BooleanYesNoDictionary'
import { ConfigType } from '@/model/system/config/ConfigType'

/**
 * # 参数配置实体
 *
 * @author Hamm.cn
 */
@KModel({
  label: '配置',
})
export class ConfigEntity extends BaseEntity {
  @KTable({
    copy: true,
    order: 88,
  })
  @KForm({
    requiredString: true,
  })
  @KField({
    label: '配置名称',
  })
  @KSearch()
  name!: string

  @KField({
    label: '配置描述',
  })
  description!: string

  @KTable({
    copy: true,
    width: 300,
    order: 99,
  })
  @KSearch()
  @KForm({
    requiredString: true,
  })
  @KField({
    label: '配置标识',
  })
  flag!: string

  @KSearch({
    width: 120,
  })
  @KTable({
    color: true,
    width: 100,
    order: 77,
  })
  @KForm({
    requiredNumber: true,
    clearable: false,
    defaultValue: ConfigType.BOOLEAN.key,
  })
  @KField({
    label: '配置类型',
    dictionary: ConfigType,
  })
  type!: number

  @KTable({
    color: true,
    width: 80,
    order: 66,
  })
  @KField({
    label: '系统配置',
    dictionary: BooleanYesNoDictionary,
  })
  isSystem!: boolean

  @KTable({
    width: 200,
  })
  @KForm({
    requiredString: true,
  })
  @KField({
    label: '配置参数',
  })
  config!: string

  @KTable({
    removed: true,
  })
  declare updateTime: number
}
