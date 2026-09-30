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
  /**
   * ### 列宽（口径见 `src/framework/types.ts`）
   *
   * 本页 4 个文本列原先都没声明宽度 ⇒ 一律吃 `min-width: 120` 并平分剩余宽度，
   * 实测编码所属字段 182/84、规则前缀 181/23、规则模板 181/60、下一个编码 181/105，
   * 而 `序列号初始长度` 却定宽 160 装一个 8px 的数字。改为：只有「规则模板」留弹性，
   * 其余按内容定宽，序列号初始长度收到 130（刚好放得下 7 字表头）。
   */
  @KForm({
    requiredNumber: true,
  })
  @KTable({
    width: 150,
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
    width: 120,
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
    width: 130,
    force: true,
  })
  @KField({
    label: '序列号初始长度',
  })
  snLength!: number

  @KTable({
    minWidth: 180,
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

  @KTable({
    width: 170,
  })
  @KField({
    label: '下一个编码',
  })
  nextCode!: string

  currentSn!: number
}
