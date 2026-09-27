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
import { DataPermissionSubjectType } from './DataPermissionSubjectType'

/**
 * # 行级数据策略
 *
 * 与后端 `cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity` 对齐。
 */
@KModel({
  label: '行级数据策略',
})
export class DataPolicyEntity extends BaseEntity {
  @KSearch()
  @KTable({
    copy: true,
    force: true,
    width: 200,
  })
  @KForm({
    requiredString: true,
  })
  @KField({
    label: '策略编码',
  })
  policyCode!: string

  @KTable({
    width: 100,
  })
  @KForm({
    requiredNumber: true,
  })
  @KField({
    label: '数据资源ID',
  })
  resourceId!: number

  @KSearch()
  @KTable({
    width: 120,
  })
  @KForm({
    requiredString: true,
  })
  @KField({
    dictionary: DataPermissionSubjectType,
    label: '主体类型',
  })
  subjectType!: string

  @KTable({
    width: 110,
  })
  @KForm({
    number: true,
    placeholder: '主体ID（对应用户/部门/仓库等实体ID）',
  })
  @KField({
    label: '主体ID',
  })
  subjectId!: number

  @KTable({
    width: 110,
  })
  @KForm({
    placeholder: '留空或 default 表示通配全部角色',
  })
  @KField({
    label: '角色编码',
  })
  roleCode!: string

  @KTable({
    width: 100,
  })
  @KForm({
    requiredString: true,
  })
  @KField({
    dictionary: DataPermissionActionType,
    label: '动作',
  })
  actionName!: string

  /**
   * 条件树 JSON。后端会做白名单/深度/裸 SQL 校验，非法直接拒绝保存。
   */
  @KTable({
    width: 260,
    copy: true,
  })
  @KForm({
    textarea: true,
    placeholder: '条件树 JSON 规则，留空为不过滤',
  })
  @KField({
    label: '条件树JSON',
  })
  conditionJson!: string

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
