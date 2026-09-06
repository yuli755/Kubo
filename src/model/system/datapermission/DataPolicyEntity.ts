import { Field, Form, Model, Search, Table } from '@airpower/web'
import { BaseEntity } from '@/base/BaseEntity'
import { BooleanYesNoDictionary } from '@/model/common/BooleanYesNoDictionary'
import { DataPermissionActionType } from './DataPermissionActionType'
import { DataPermissionSubjectType } from './DataPermissionSubjectType'

/**
 * # 行级数据策略
 *
 * 与后端 `cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity` 对齐。
 */
@Model({
  label: '行级数据策略',
})
export class DataPolicyEntity extends BaseEntity {
  @Search()
  @Table({
    copy: true,
    force: true,
    width: 200,
  })
  @Form({
    requiredString: true,
  })
  @Field({
    label: '策略编码',
  })
  policyCode!: string

  @Table({
    width: 100,
  })
  @Form({
    requiredNumber: true,
  })
  @Field({
    label: '数据资源ID',
  })
  resourceId!: number

  @Search()
  @Table({
    width: 120,
  })
  @Field({
    dictionary: DataPermissionSubjectType,
    label: '主体类型',
  })
  subjectType!: string

  @Table({
    width: 110,
  })
  @Field({
    label: '主体ID',
  })
  subjectId!: number

  @Table({
    width: 110,
  })
  @Field({
    label: '角色编码',
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

  /**
   * 条件树 JSON。后端会做白名单/深度/裸 SQL 校验，非法直接拒绝保存。
   */
  @Table({
    width: 260,
    copy: true,
  })
  @Field({
    label: '条件树JSON',
  })
  conditionJson!: string

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
