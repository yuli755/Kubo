import { Field, Form, Model, Search, Table } from '@airpower/web'
import { BaseEntity } from '@/base/BaseEntity'
import { BooleanYesNoDictionary } from '@/model/common/BooleanYesNoDictionary'
import { DataPermissionActionType } from './DataPermissionActionType'
import { DataPermissionMaskType } from './DataPermissionMaskType'

/**
 * # 列级脱敏策略
 *
 * 与后端 `cn.hamm.spms.module.system.datapermission.entity.DataFieldPolicyEntity` 对齐。
 */
@Model({
  label: '列级脱敏策略',
})
export class DataFieldPolicyEntity extends BaseEntity {
  @Table({
    width: 110,
  })
  @Form({
    requiredNumber: true,
  })
  @Field({
    label: '数据资源ID',
  })
  resourceId!: number

  /**
   * 后端返回的嵌套资源对象；前端只用 resourceKey 展示，不参与编辑
   * （编辑走 resourceId，避免提交时把整个资源实体回传）
   */
  @Table({
    hide: true,
  })
  @Field({
    label: '数据资源',
  })
  resource!: { resourceKey?: string }

  @Search()
  @Table({
    width: 140,
    copy: true,
  })
  @Form({
    requiredString: true,
  })
  @Field({
    label: '字段名',
  })
  fieldName!: string

  @Table({
    width: 110,
  })
  @Field({
    label: '角色编码（default 为通配）',
  })
  roleCode!: string

  @Table({
    width: 100,
  })
  @Field({
    dictionary: DataPermissionActionType,
    label: '动作',
  })
  actionName!: string

  @Table({
    width: 110,
  })
  @Form({
    requiredString: true,
  })
  @Field({
    dictionary: DataPermissionMaskType,
    label: '脱敏类型',
  })
  maskType!: string

  @Table({
    width: 120,
  })
  @Field({
    label: '遮蔽表达式',
  })
  maskExpression!: string

  @Table({
    width: 90,
  })
  @Field({
    // 挂布尔字典：key 就是 true/false，AFormField 会渲染成下拉并提交真布尔
    // （不挂的话会退化成文本框，提交 "true"/"" 这种字符串，空串还可能把非空列写成 null）
    dictionary: BooleanYesNoDictionary,
    label: '允许明文',
  })
  allowPlaintext!: boolean

  @Table({
    width: 90,
  })
  @Field({
    label: '优先级',
  })
  priority!: number

  @Table({
    width: 90,
  })
  @Field({
    // 挂布尔字典：key 就是 true/false，AFormField 会渲染成下拉并提交真布尔
    // （不挂的话会退化成文本框，提交 "true"/"" 这种字符串，空串还可能把非空列写成 null）
    dictionary: BooleanYesNoDictionary,
    label: '启用',
  })
  enabled!: boolean
}
