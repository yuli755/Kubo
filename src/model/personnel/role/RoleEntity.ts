import type { IPayload } from '@airpower/web'
import { Field, Form, Model, Search, Table, Type } from '@airpower/web'
import { BaseEntity } from '@/base/BaseEntity'
import {
  Field as KField,
  Form as KForm,
  Model as KModel,
  Search as KSearch,
  Table as KTable,
  Type as KType,
} from '@/framework'
import { MenuEntity } from '@/model/system/menu/MenuEntity'
import { PermissionEntity } from '@/model/system/permission/PermissionEntity'

/**
 * # 角色
 * @author Hamm.cn
 */

@Model({
  label: '角色',
})
@KModel({
  label: '角色',
})
export class RoleEntity extends BaseEntity implements IPayload {
  @Table({
    force: true,
  })
  @Search()
  @Form({
    requiredString: true,
  })
  @Field({
    label: '角色名称',
  })
  @KTable({ order: 100, minWidth: 160, force: true, sortable: true })
  @KForm({ requiredString: true, maxLength: 32 })
  @KField({ label: '角色名称' })
  @KSearch()
  name!: string

  @Table({
    force: true,
  })
  @Search()
  @Form({
    placeholder: '不填按编码规则自动生成',
  })
  @Field({
    label: '角色编码',
  })
  @KTable({ order: 90, minWidth: 200, force: true, copy: true })
  @KForm({ placeholder: '不填按编码规则自动生成', maxLength: 32 })
  @KField({ label: '角色编码' })
  @KSearch()
  code!: string

  @Field({})
  @Type(MenuEntity, true)
  @KField({})
  @KType(MenuEntity)
  menuList!: MenuEntity[]

  @Field({})
  @Type(PermissionEntity, true)
  @KField({})
  @KType(PermissionEntity)
  permissionList!: PermissionEntity[]

  @Table({
    removed: false,
  })
  @Search({
    hide: false,
  })
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

  getPayloadLabel() {
    return this.name
  }
}
