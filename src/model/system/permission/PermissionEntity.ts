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
  @KTable({
    width: 100,
  })
  declare id: number

  @KTable({
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
    width: 100,
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
