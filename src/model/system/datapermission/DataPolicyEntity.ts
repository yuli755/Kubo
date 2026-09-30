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
  /**
   * ### 列宽与默认隐藏（口径见 `src/framework/types.ts`）
   *
   * 本页原先声明宽合计 1426px，可用只有 1131px ⇒ 横向滚动，而撑宽它的三列内容几乎是空的：
   * `数据资源ID` 100/5、`主体ID` 110/5、`条件树JSON` 260/9 —— 全是裸外键 ID 与 JSON blob，
   * 属于「排查时才看」的信息 ⇒ `hide: true` 默认不显示（列选择器仍可勾回，`hide` 不是权限控制）。
   * 腾出的宽度给唯一真正被截断的 `策略编码`（实测 200 宽 / 292px 内容），改成弹性列。
   */
  @KSearch()
  @KTable({
    copy: true,
    force: true,
    minWidth: 280,
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
    hide: true,
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
    width: 130,
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
    hide: true,
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
    width: 120,
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
    hide: true,
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
