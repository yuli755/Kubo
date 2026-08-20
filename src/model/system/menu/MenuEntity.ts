import type { IMenu } from '@airpower/web'
import type { RouteMeta } from 'vue-router'
import { Field, Form, Model, Search, Table, Type } from '@airpower/web'
import { BaseEntity } from '@/base/BaseEntity'

/**
 * # 菜单
 * @author Hamm.cn
 */
@Model({
  label: '菜单',
})
export class MenuEntity extends BaseEntity implements IMenu {
  @Table({
    force: true,
  })
  @Search()
  @Form({
    requiredString: true,
  })
  @Field({
    label: '菜单名称',
  })
  name!: string

  @Table({
    copy: true,
    force: true,
  })
  @Form({
    requiredString: true,
  })
  @Field({
    label: '菜单路径',
  })
  @Search()
  path!: string

  @Form()
  @Field({
    label: '菜单图标',
  })
  icon!: string

  @Table({
    copy: true,
  })
  @Form()
  @Field({
    label: '组件地址',
  })
  @Search()
  component!: string

  @Form({
    max: 1000,
    min: 0,
    number: true,
    filterable: true,
    requiredNumber: true,
    defaultValue: 1,
  })
  @Table({
    width: 100,
  })
  @Field({
    label: '排序编号',
  })
  orderNo!: number

  @Field({
    label: '父级ID',
  })
  parentId!: number

  @Field({})
  @Type(MenuEntity, true)
  children!: this[]

  @Type(MenuEntity)
  parent!: this

  /** 菜单绑定的权限标识（后端返回，用于注入路由 meta.permission） */
  permission?: string

  /** 路由元数据（运行时由 applyMenuPermissions 写入） */
  meta?: RouteMeta
}
