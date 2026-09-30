import type { IPayload } from '@/framework'
import { BaseEntity } from '@/base/BaseEntity'
import { Field as KField, Form as KForm, Model as KModel, Search as KSearch, Table as KTable, Type as KType } from '@/framework'
import { MenuEntity } from '@/model/system/menu/MenuEntity'
import { PermissionEntity } from '@/model/system/permission/PermissionEntity'

/**
 * # 角色
 * @author Hamm.cn
 */

@KModel({
  label: '角色',
})
export class RoleEntity extends BaseEntity implements IPayload {
  @KTable({ order: 100, minWidth: 180, force: true, sortable: true })
  @KForm({ requiredString: true, maxLength: 32 })
  @KField({ label: '角色名称' })
  @KSearch()
  name!: string

  /**
   * ### 角色编码
   *
   * 定宽 180（实测 90px）。原 `minWidth: 200` 与名称一起瓜分剩余宽度，
   * 实测名称 310 / 编码 386，内容只有 162 / 90px。列宽口径见 `src/framework/types.ts`。
   */
  @KTable({ order: 90, width: 180, force: true, copy: true })
  @KForm({ placeholder: '不填按编码规则自动生成', maxLength: 32 })
  @KField({ label: '角色编码' })
  @KSearch()
  code!: string

  @KField({})
  @KType(MenuEntity)
  menuList!: MenuEntity[]

  @KField({})
  @KType(PermissionEntity)
  permissionList!: PermissionEntity[]

  @KTable({
    order: -98,
    width: 100,
    /**
     * 状态列按「启用中 / 已禁用」渲染，而不是框架对布尔值的默认「是 / 否」——
     * 行操作里的按钮读的也是 `isDisabled`，两者必须说同一句话，否则用户会看到
     * 「是」配上「禁用」按钮这种自相矛盾的组合。
     */
    formatter: (row: never) => ((row as { isDisabled?: boolean }).isDisabled ? '已禁用' : '启用中'),
  })
  @KField({ label: '状态' })
  declare isDisabled: boolean

  /**
   * 基础数据表统一补「创建时间」列，理由同 `StorageEntity.createTime`。
   *
   * `order` 用 -99 而不是别处的 -90：本实体的状态列钉在 -98，而列是**按 order 降序**排的，
   * 要让「状态」仍在「创建时间」左边（与单据类 `状态 -80 / 创建时间 -90` 的视觉次序一致），
   * 这里只能取一个比 -98 更小的值。
   */
  @KTable({ order: -99, width: 170, datetime: true })
  @KField({ label: '创建时间' })
  declare createTime: number

  getPayloadLabel() {
    return this.name
  }
}
