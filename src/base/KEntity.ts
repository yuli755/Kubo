import { Field, Model, RootEntity, Table } from '@/framework'

/**
 * # K 系实体基类（新底座）
 *
 * 新页面用这个基类，而不是 `@/base/BaseEntity`（后者继承 airpower 的 `RootEntity`，
 * 装饰器写进 airpower 的元数据注册表）。
 *
 * ## 为什么要有两套基类并存
 * `@/base/BaseEntity` 被 144 个存量页面依赖，而 airpower 的 `ATable`/`AFormField` 读的是**它自己**
 * 的注册表：直接把基类换掉，存量页面会「列头有、表单空」。所以新底座不改造旧基类，
 * 而是并行提供这一份，按域迁移（F4）时逐域把 `extends BaseEntity` 换成 `extends KEntity`，
 * 换完一个域，那个域就不再依赖 airpower 的元数据。
 *
 * ## 公共列与后端一致
 * `id / createTime / updateTime / isDisabled / isPublished / tenantId / creatorId`
 * 对应 `CurdEntity` + 本仓 `BaseEntity` + `TenantAwareEntity`（见 Flyway 基线 V1）。
 */
@Model({ label: '数据' })
export class KEntity extends RootEntity {
  /** 创建时间（后端是毫秒时间戳） */
  @Field({ label: '创建时间' })
  createTime?: number

  /** 更新时间 */
  @Field({ label: '更新时间' })
  updateTime?: number

  /** 是否启用（`isDisabled` 取反展示，避免每个页面各写一次「禁用/启用」转换） */
  @Table({ order: -98, width: 90, label: '状态' })
  @Field({ label: '状态' })
  isPublished?: boolean

  /** 租户 id（多租户隔离列） */
  tenantId?: number

  /** 创建人 id */
  creatorId?: number
}
