import { BaseEntity } from '@/base/BaseEntity'
import {
  Field as KField,
  Form as KForm,
  Model as KModel,
  Search as KSearch,
  Table as KTable,
} from '@/framework'

/**
 * # 数据资源
 *
 * 与后端 `cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity` 对齐。
 */
@KModel({
  label: '数据资源',
})
export class DataResourceEntity extends BaseEntity {
  @KSearch()
  @KTable({
    copy: true,
    force: true,
    width: 180,
  })
  @KForm({
    requiredString: true,
  })
  @KField({
    label: '资源标识',
  })
  resourceKey!: string

  @KTable({
    width: 140,
  })
  @KForm({
    requiredString: true,
  })
  @KField({
    label: '表名',
  })
  tableName!: string

  @KTable({
    hide: true,
  })
  @KField({
    label: '表别名',
  })
  tableAlias!: string

  @KTable({
    hide: true,
  })
  @KField({
    label: '租户列',
  })
  tenantColumn!: string

  /**
   * ### 列宽与列标题（口径见 `src/framework/types.ts`）
   *
   * 本页原先声明宽合计 1266px，可用宽度只有 1131px ⇒ 横向滚动。三处调整：
   * 1. 长括号标题把列撑宽（`归属列（可按维度登记）` 200、`可过滤列（条件树白名单）` 200），
   *    用 `@KTable({ label })` 只缩短**列**标题，`@KField` 的完整标题仍给表单/详情用；
   * 2. `可过滤列` 实测内容只有 5px（逗号分隔的列名，多数资源为空）⇒ 默认隐藏，列选择器可勾回；
   * 3. `支持的动作` 实测内容 314px 却被压到 200 ⇒ 改成弹性列 `minWidth: 260`。
   */
  @KTable({
    width: 160,
    label: '归属列',
  })
  @KField({
    label: '归属列（可按维度登记）',
  })
  ownerColumns!: string

  @KTable({
    hide: true,
    label: '可过滤列',
  })
  @KField({
    label: '可过滤列（条件树白名单）',
  })
  filterableColumns!: string

  @KTable({
    minWidth: 260,
  })
  @KField({
    label: '支持的动作',
  })
  supportedActions!: string

  @KTable({
    width: 100,
  })
  @KField({
    label: '状态',
  })
  status!: string
}
