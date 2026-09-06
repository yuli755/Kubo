import { Field, Form, Model, Search, Table } from '@airpower/web'
import { BaseEntity } from '@/base/BaseEntity'

/**
 * # 数据资源
 *
 * 与后端 `cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity` 对齐。
 */
@Model({
  label: '数据资源',
})
export class DataResourceEntity extends BaseEntity {
  @Search()
  @Table({
    copy: true,
    force: true,
    width: 180,
  })
  @Form({
    requiredString: true,
  })
  @Field({
    label: '资源标识',
  })
  resourceKey!: string

  @Table({
    width: 140,
  })
  @Form({
    requiredString: true,
  })
  @Field({
    label: '表名',
  })
  tableName!: string

  @Table({
    hide: true,
  })
  @Field({
    label: '表别名',
  })
  tableAlias!: string

  @Table({
    hide: true,
  })
  @Field({
    label: '租户列',
  })
  tenantColumn!: string

  @Table({
    width: 200,
  })
  @Field({
    label: '归属列（所有者判定用）',
  })
  ownerColumns!: string

  @Table({
    width: 200,
  })
  @Field({
    label: '可过滤列（条件树白名单）',
  })
  filterableColumns!: string

  @Table({
    width: 200,
  })
  @Field({
    label: '支持的动作',
  })
  supportedActions!: string

  @Table({
    width: 100,
  })
  @Field({
    label: '状态',
  })
  status!: string
}
