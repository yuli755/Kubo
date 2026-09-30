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
  /**
   * ### 配置名称
   *
   * 唯一的弹性列（口径见 `src/framework/types.ts`）：标识/类型/系统配置/参数都定宽，
   * 剩余宽度全给名称。原先名称与参数一起吃弹性宽度，实测名称 255 / 内容 126。
   */
  @KTable({
    copy: true,
    minWidth: 180,
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

  /**
   * ### 配置标识
   *
   * 定宽 260（实测内容 210px，形如 `PICKING_BILL_AUTO_AUDIT`）。原 300 是按最长标识拍的，
   * 多出的 40px 在只有 5 列的页面上就是把名称列挤窄的那 40px。
   */
  @KTable({
    copy: true,
    width: 260,
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
