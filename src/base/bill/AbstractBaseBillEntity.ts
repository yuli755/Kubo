import type { AbstractBaseBillDetailEntity } from './detail/AbstractBaseBillDetailEntity'
import type { IPayload, WebEnum } from '@/framework'
import { Field, Form, Search, Table } from '@/framework'
import { BaseEntity } from '../BaseEntity'

/**
 * # 单据基类（新底座）
 *
 * 单据 = 普通实体 + 单据编号 + 状态机 + 明细数组 + 驳回原因。五个单据实体
 * （入库 / 出库 / 移库 / 采购 / 销售）都继承它，`canAudit()` / `canReject()` /
 * `isRejected()` 由子类给的状态枚举驱动 —— 页面不重算状态。
 *
 * ## 与 airpower 版的关系
 * 原 `AbstractBaseBillEntity` 的装饰器写进 airpower 注册表；2026-09-18 连根拔掉
 * `@airpower/web` 后，这里改为框架装饰器，**同一份元数据、同一批字段**，
 * 子类（各单据实体）的 `@K*` 声明不变。
 *
 * ## 为什么 `status` 不给 `@Form`
 * 状态是状态机的结果（审核 / 驳回 / 作业推进），不是用户能填的字段。
 * 表单里出现它就等于给了"手改状态"的后门 —— 这条口径在 wms/channel 两批里都验证过。
 */
export abstract class AbstractBaseBillEntity<D extends AbstractBaseBillDetailEntity>
  extends BaseEntity
  implements IPayload {
  /** 单据编号 */
  @Table({ order: 99, force: true, copy: true })
  @Search()
  @Form({ placeholder: '不填写按编码规则自动生成' })
  @Field({ label: '单据编号' })
  billCode!: string

  /** 单据状态码（字典由子类的 `status` 声明提供） */
  @Search({ width: 120 })
  @Field({ label: '单据状态' })
  abstract status: number

  /** 单据明细列表 */
  abstract details: D[]

  /** 驳回原因（只读展示，不进表单） */
  @Field({ label: '驳回原因' })
  rejectReason?: string

  /** 审核中的状态 */
  abstract getAuditingStatus(): WebEnum

  /** 已审核的状态 */
  abstract getAuditedStatus(): WebEnum

  /** 已拒绝的状态 */
  abstract getRejectedStatus(): WebEnum

  /** 选择器回显用：单据编号 */
  getPayloadLabel(): string {
    return this.billCode
  }

  /** 是否可审核（审核中的单据才能审核） */
  canAudit(): boolean {
    return this.getAuditingStatus().equalsKey(this.status)
  }

  /** 是否可驳回（与可审核同条件：都在"审核中"） */
  canReject(): boolean {
    return this.getAuditingStatus().equalsKey(this.status)
  }

  /** 是否已驳回（被驳回的单据才允许再编辑） */
  isRejected(): boolean {
    return this.getRejectedStatus().equalsKey(this.status)
  }
}
