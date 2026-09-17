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

  @KTable({
    width: 200,
  })
  @KField({
    label: '归属列（可按维度登记）',
  })
  ownerColumns!: string

  @KTable({
    width: 200,
  })
  @KField({
    label: '可过滤列（条件树白名单）',
  })
  filterableColumns!: string

  @KTable({
    width: 200,
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
