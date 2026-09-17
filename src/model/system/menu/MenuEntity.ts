import type { RouteMeta } from 'vue-router'
import type { IMenu } from '@/framework'
import { BaseEntity } from '@/base/BaseEntity'
import {
  Field as KField,
  Form as KForm,
  Model as KModel,
  Search as KSearch,
  Table as KTable,
  Type as KType,
} from '@/framework'

/**
 * # 菜单
 * @author Hamm.cn
 */
@KModel({
  label: '菜单',
})
export class MenuEntity extends BaseEntity implements IMenu {
  @KTable({
    force: true,
  })
  @KSearch()
  @KForm({
    requiredString: true,
  })
  @KField({
    label: '菜单名称',
  })
  name!: string

  @KTable({
    copy: true,
    force: true,
  })
  @KForm({
    requiredString: true,
  })
  @KField({
    label: '菜单路径',
  })
  @KSearch()
  path!: string

  @KForm()
  @KField({
    label: '菜单图标',
  })
  icon!: string

  @KTable({
    copy: true,
  })
  @KForm()
  @KField({
    label: '组件地址',
  })
  @KSearch()
  component!: string

  @KForm({
    max: 1000,
    min: 0,
    number: true,
    filterable: true,
    requiredNumber: true,
    defaultValue: 1,
  })
  @KTable({
    width: 100,
  })
  @KField({
    label: '排序编号',
  })
  orderNo!: number

  @KField({
    label: '父级ID',
  })
  parentId!: number

  @KField({})
  @KType(MenuEntity)
  @KType(MenuEntity)
  children!: this[]

  @KType(MenuEntity)
  parent!: this

  /** 菜单绑定的权限标识（后端返回，用于注入路由 meta.permission） */
  @KForm()
  @KField({
    label: '权限标识',
  })
  permission?: string

  /** 路由元数据（运行时由 applyMenuPermissions 写入） */
  meta?: RouteMeta
}
