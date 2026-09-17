import type { ITree } from '@airpower/web'
import { Field, Form, Model, Search, Table, Type } from '@airpower/web'
import { BaseEntity } from '@/base/BaseEntity'
// 同 `MenuEntity`：先补 `@KType` 让授权弹窗拿到的权限树能递归实例化（见那边的说明），
// 其余 K 装饰器随 system 域迁移补齐。
import { Type as KType } from '@/framework'
import { PermissionSystemEnum } from './PermissionSystemEnum'
import { PermissionTypeEnum } from './PermissionTypeEnum'

/**
 * # 权限
 * @author Hamm.cn
 */
@Model({
  label: '权限',
})
export class PermissionEntity extends BaseEntity implements ITree {
  @Table({
    width: 100,
  })
  declare id: number

  @Table({
    force: true,
  })
  @Search()
  @Form({
    requiredString: true,
  })
  @Field({
    label: '权限名称',
  })
  name!: string

  @Table({
    force: true,
    copy: true,
  })
  @Form({
    requiredString: '请输入权限标识...',
  })
  @Field({
    label: '权限标识',
  })
  @Search()
  identity!: string

  @Table({
    color: true,
    width: 100,
  })
  @Search({
    width: 120,
  })
  @Field({
    label: '权限类型',
    dictionary: PermissionTypeEnum,
  })
  type!: number

  @Table({
    color: true,
    width: 100,
    order: -100,
  })
  @Field({
    label: '系统权限',
    dictionary: PermissionSystemEnum,
  })
  isSystem!: boolean

  @Field({
    label: '父级ID',
  })
  parentId!: number

  @Field({})
  @Type(PermissionEntity, true)
  @KType(PermissionEntity)
  children!: this[]

  @Type(PermissionEntity)
  parent!: this

  @Table({
    removed: true,
  })
  declare createTime: number

  @Table({
    removed: true,
  })
  declare updateTime: number

  @Table({
    removed: true,
  })
  declare isDisabled: boolean
}
