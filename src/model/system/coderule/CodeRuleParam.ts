import { Field as KField, RootModel } from '@/framework'

/**
 * # 编码规则参数
 *
 * @author Hamm.cn
 */
export class CodeRuleParam extends RootModel {
  @KField({
    label: '占位符',
  })
  label!: string

  @KField({
    label: '参数值',
  })
  value!: number

  @KField({
    label: '示例',
  })
  demo!: string

  @KField({
    label: '标题',
  })
  desc!: string
}
