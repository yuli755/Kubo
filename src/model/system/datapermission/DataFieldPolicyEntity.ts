import { BaseEntity } from '@/base/BaseEntity'
import {
  Field as KField,
  Form as KForm,
  Model as KModel,
  Search as KSearch,
  Table as KTable,
} from '@/framework'
import { BooleanYesNoDictionary } from '@/model/common/BooleanYesNoDictionary'
import { DataPermissionActionType } from './DataPermissionActionType'
import { DataPermissionMaskType } from './DataPermissionMaskType'

/**
 * # 列级脱敏策略
 *
 * 与后端 `cn.hamm.spms.module.system.datapermission.entity.DataFieldPolicyEntity` 对齐。
 */
@KModel({
  label: '列级脱敏策略',
})
export class DataFieldPolicyEntity extends BaseEntity {
  /**
   * ### 列宽与默认隐藏（口径见 `src/framework/types.ts`）
   *
   * 本页原先声明宽合计 1206px，可用 1142px ⇒ 横向滚动；同时 `脱敏类型` 实测 110 宽 / 126px 内容
   * 被截断，而 `数据资源ID`（110/16）与 `遮蔽表达式`（120/5）几乎不占内容 ⇒ 两列默认隐藏，
   * 腾出的宽度给 `脱敏类型`（110→140）和弹性的 `字段名`。
   * `角色编码（default 为通配）` 这个 12 字标题本身就把列撑到 110，用 `@KTable({ label })`
   * 只缩短列标题，通配说明留在 `@KForm` 的 placeholder 里（表单本来就有）。
   */
  @KTable({
    width: 110,
    hide: true,
  })
  @KForm({
    requiredNumber: true,
  })
  @KField({
    label: '数据资源ID',
  })
  resourceId!: number

  /**
   * 后端返回的嵌套资源对象；前端只用 resourceKey 展示，不参与编辑
   * （编辑走 resourceId，避免提交时把整个资源实体回传）
   */
  @KTable({
    hide: true,
  })
  @KField({
    label: '数据资源',
  })
  resource!: { resourceKey?: string }

  @KSearch()
  @KTable({
    minWidth: 160,
    copy: true,
  })
  @KForm({
    requiredString: true,
  })
  @KField({
    label: '字段名',
  })
  fieldName!: string

  @KTable({
    width: 120,
    label: '角色编码',
  })
  @KForm({
    placeholder: '留空或 default 表示通配全部角色',
  })
  @KField({
    label: '角色编码（default 为通配）',
  })
  roleCode!: string

  @KTable({
    width: 100,
  })
  @KForm({
    // 库里 `action_name` 是 NOT NULL：不声明必填的话，留空会提交成空串/undefined，
    // 报错发生在后端持久层，界面只给一个 500。
    requiredString: true,
  })
  @KField({
    dictionary: DataPermissionActionType,
    label: '动作',
  })
  actionName!: string

  @KTable({
    width: 140,
  })
  @KForm({
    requiredString: true,
  })
  @KField({
    dictionary: DataPermissionMaskType,
    label: '脱敏类型',
  })
  maskType!: string

  @KTable({
    width: 120,
    hide: true,
  })
  @KForm({
    placeholder: '脱敏表达式（例如自定义掩码规则）',
  })
  @KField({
    label: '遮蔽表达式',
  })
  maskExpression!: string

  @KTable({
    width: 90,
  })
  @KForm({
    switch: true,
    defaultValue: false,
  })
  @KField({
    // 挂布尔字典：key 就是 true/false，AFormField 会渲染成下拉并提交真布尔
    // （不挂的话会退化成文本框，提交 "true"/"" 这种字符串，空串还可能把非空列写成 null）
    dictionary: BooleanYesNoDictionary,
    label: '允许明文',
  })
  allowPlaintext!: boolean

  @KTable({
    width: 90,
  })
  @KForm({
    number: true,
    defaultValue: 0,
  })
  @KField({
    label: '优先级',
  })
  priority!: number

  @KTable({
    width: 90,
  })
  @KForm({
    switch: true,
    defaultValue: true,
  })
  @KField({
    // 挂布尔字典：key 就是 true/false，AFormField 会渲染成下拉并提交真布尔
    // （不挂的话会退化成文本框，提交 "true"/"" 这种字符串，空串还可能把非空列写成 null）
    dictionary: BooleanYesNoDictionary,
    label: '启用',
  })
  enabled!: boolean
}
