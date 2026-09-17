import { Field as KField, Transformer } from '@/framework'

/**
 * # 编码规则表格
 *
 * @author Hamm.cn
 */
export class CodeRuleField extends Transformer {
  @KField({
    label: '默认前缀',
  })
  defaultPrefix!: string

  @KField({
    label: '编码字段名称',
  })
  label!: string

  @KField({
    label: '表格ID',
  })
  key!: number
}
