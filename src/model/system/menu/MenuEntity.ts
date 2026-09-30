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
  /**
   * ### 列宽口径（见 `src/framework/types.ts`）
   *
   * 三个文本列原先都没有 `@KTable` 宽度 ⇒ 一律吃 KTable 的 `min-width: 120` 默认值，
   * 剩余宽度被平均瓜分：实测菜单名称 263（内容 91px）、菜单路径 261（内容 **352px，被截断**）、
   * 组件地址 261（内容 150px）。路径是唯一真正需要弹性的列（`/console/system/datapermission/fieldPolicy`
   * 这种深路径），因此改为：名称定宽、路径与组件地址按 `minWidth` 弹性分配（3:2），
   * 路径拿到约 350px 正好够，组件地址约 230px 也够。
   */
  @KTable({
    width: 170,
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
    minWidth: 300,
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
    minWidth: 200,
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
