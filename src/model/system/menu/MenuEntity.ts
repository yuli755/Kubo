import type { IMenu } from '@airpower/web'
import type { RouteMeta } from 'vue-router'
import { Field, Form, Model, Search, Table, Type } from '@airpower/web'
import { BaseEntity } from '@/base/BaseEntity'
// 只先补 `@KType`（不补整套 K 装饰器）：角色授权弹窗（F4 批 3）用
// `KMenuService.getTreeList` 取菜单树，`fill()` 靠这个类型提示把 `children`
// 递归实例化成 `MenuEntity` —— 少了它，勾选子级菜单后提交会调不到 `copyOnlyId()`
// （`KRoleService.authorizeMenu` 会对每一项调它）。system 域整体迁移时再补齐其余装饰器。
import { Type as KType } from '@/framework'

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
  @KType(MenuEntity)
  children!: this[]

  @Type(MenuEntity)
  parent!: this

  /** 菜单绑定的权限标识（后端返回，用于注入路由 meta.permission） */
  @Form()
  @Field({
    label: '权限标识',
  })
  permission?: string

  /** 路由元数据（运行时由 applyMenuPermissions 写入） */
  meta?: RouteMeta
}
