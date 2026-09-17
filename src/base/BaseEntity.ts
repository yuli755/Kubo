import { Field, Model, RootEntity } from '@/framework'
import { PublishStatusEnum } from '@/model/common/PublishStatusEnum'

/**
 * # 数据库实体基类（新底座）
 *
 * 所有业务实体的父类。`RootEntity`（`@/framework`）提供 `id` / `copyOnlyId()`；
 * 这里补上后端 `CurdEntity` + `TenantAwareEntity` 的公共列（见 Flyway 基线 V1）。
 *
 * ## 这是唯一一份基类（2026-09-18 大清理）
 * 之前有两套并行基类：`BaseEntity`（继承 airpower 的 `RootEntity`，装饰器写进 airpower
 * 注册表）与 `KEntity`（写进 `@/framework` 注册表）。迁移期这是必要的；`@airpower/web`
 * 连根拔掉后两套注册表只剩一套，双基类失去存在理由 —— `KEntity` 已删除，
 * 所有实体统一继承本类。
 *
 * ## 元数据口径（与迁移期 `KEntity` 逐字一致，勿随手改）
 * - **列表列不进基类**：`isPublished` / `isDisabled` 只给 `@Field`。各域的状态列语义不同
 *   （发布状态 / 启停 / 单据状态），基类抢一个列名会出现两列同名「状态」。
 *   需要状态列时由实体自己 `@Table` 声明（`personnelDomain.spec.ts` 有断言钉住这点）。
 * - `createTime` / `updateTime` 同理只给 `@Field`：需要它们的表自己加列。
 * - `tenantId` / `creatorId` 是隔离与审计列，不给表单（用户不该手填）。
 */
@Model({ label: '数据' })
export class BaseEntity extends RootEntity {
  /** 创建时间（后端是毫秒时间戳） */
  @Field({ label: '创建时间' })
  createTime?: number

  /** 更新时间 */
  @Field({ label: '更新时间' })
  updateTime?: number

  /** 发布状态（`PublishStatusEnum`：已发布 / 待发布） */
  @Field({ label: '发布状态', dictionary: PublishStatusEnum })
  declare isPublished: boolean

  /** 启停状态（列表行的启禁用按钮读它） */
  @Field({ label: '状态' })
  declare isDisabled: boolean

  /** 租户 id（多租户隔离列） */
  tenantId?: number

  /** 创建人 id */
  creatorId?: number
}
