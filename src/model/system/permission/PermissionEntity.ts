import type { ITree } from '@/framework'
import { BaseEntity } from '@/base/BaseEntity'
import {
  Field as KField,
  Form as KForm,
  Model as KModel,
  Search as KSearch,
  Table as KTable,
  Type as KType,
} from '@/framework'
import { PermissionSystemEnum } from './PermissionSystemEnum'
import { PermissionTypeEnum } from './PermissionTypeEnum'

/**
 * # 权限
 * @author Hamm.cn
 */
@KModel({
  label: '权限',
})
export class PermissionEntity extends BaseEntity implements ITree {
  /**
   * ### 权限ID（默认隐藏）
   *
   * 实测这一列在表头上就是一个裸 `id`：没有 `@KField` ⇒ 没有中文标题，宽度 100px 里
   * 只有一串自增数字，对使用者没有信息量（要看 ID 走详情抽屉）。改为 `hide: true`
   * 默认不显示，但仍留在「列显隐设置」里可勾回来 —— `hide` 只是布局取舍，不是权限控制。
   * 顺手补 `@KField` 标题，否则列选择器里也是一行 `id`。
   */
  @KTable({
    width: 100,
    hide: true,
  })
  @KField({
    label: '权限ID',
  })
  declare id: number

  /**
   * ### 名称与标识
   *
   * 权限树有 309 个节点，名称形如「数据字段策略-不分页查询」，标识形如
   * `DataFieldPolicy_getList`。实测两列各只分到 293 / 292px，而内容需要 374 / 285px
   * ⇒ 名称被截断。隐藏 `id` 列腾出 100px，并把两列都设为弹性（`minWidth`）平分剩余宽度。
   */
  @KTable({
    minWidth: 330,
    force: true,
  })
  @KSearch()
  @KForm({
    requiredString: true,
  })
  @KField({
    label: '权限名称',
  })
  name!: string

  @KTable({
    minWidth: 300,
    force: true,
    copy: true,
  })
  @KForm({
    requiredString: '请输入权限标识...',
  })
  @KField({
    label: '权限标识',
  })
  @KSearch()
  identity!: string

  @KTable({
    color: true,
    width: 100,
  })
  @KSearch({
    width: 120,
  })
  @KField({
    label: '权限类型',
    dictionary: PermissionTypeEnum,
  })
  type!: number

  @KTable({
    color: true,
    width: 90,
    order: -100,
  })
  @KField({
    label: '系统权限',
    dictionary: PermissionSystemEnum,
  })
  isSystem!: boolean

  @KField({
    label: '父级ID',
  })
  parentId!: number

  @KField({})
  @KType(PermissionEntity)
  @KType(PermissionEntity)
  children!: this[]

  @KType(PermissionEntity)
  parent!: this

  @KTable({
    removed: true,
  })
  declare createTime: number

  @KTable({
    removed: true,
  })
  declare updateTime: number

  @KTable({
    removed: true,
  })
  declare isDisabled: boolean
}
