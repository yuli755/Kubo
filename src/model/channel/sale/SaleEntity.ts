import type { WebEnum } from '@/framework'
import { AbstractBaseBillEntity } from '@/base/bill/AbstractBaseBillEntity'
import {
  Field as KField,
  Form as KForm,
  Model as KModel,
  Search as KSearch,
  Table as KTable,
  Type as KType,
} from '@/framework'
import { CustomerEntity } from '../customer/CustomerEntity'
import { SaleDetailEntity } from './SaleDetailEntity'
import { SaleStatusEnum } from './SaleStatusEnum'

/**
 * # 销售单
 *
 * F4 批 5 起双注册：K 侧（`@K*`）服务于销售单页与出库单详情里的「来源销售单」；
 * airpower 装饰器保持原样，F5 收尾时一并删除。
 *
 * 口径与采购单一致：状态**不给 `@KForm`**（状态由状态机驱动），
 * 明细只给 `@KField` + `@KType`（由 `KDetailTable` 在编辑器里渲染）。
 */
@KModel({
  label: '销售单',
  addChildPermission: 'add',
})
export class SaleEntity extends AbstractBaseBillEntity<SaleDetailEntity> {
  @KTable({ order: 99, minWidth: 180, force: true, copy: true })
  @KForm({ placeholder: '不填写按编码规则自动生成' })
  @KField({ label: '销售单号' })
  @KSearch()
  declare billCode: string

  @KTable({
    order: 90,
    minWidth: 140,
    copy: true,
    formatter: (row: never) => customerPart(row, 'code'),
  })
  @KField({ label: '客户编码' })
  customerCode!: string

  @KTable({
    order: 85,
    minWidth: 180,
    copy: true,
    formatter: (row: never) => customerPart(row, 'name'),
  })
  @KField({ label: '客户名称' })
  customerName!: string

  @KForm({ requiredNumber: true, placeholder: '请选择销售客户' })
  @KField({ label: '客户' })
  customerId!: number

  @KTable({ minWidth: 220, order: 60 })
  @KForm({ textarea: true, maxLength: 80 })
  @KField({ label: '销售说明' })
  reason!: string

  @KTable({ width: 150, order: 50, money: true, force: true, align: 'right' })
  @KForm({ number: true, precision: 2, suffixText: '元' })
  @KField({ label: '总金额' })
  totalPrice!: number

  @KTable({ width: 110, order: -80, force: true, color: true })
  @KSearch({ width: 120 })
  @KField({ label: '销售状态', dictionary: SaleStatusEnum })
  status!: number

  @KField({ label: '销售明细' })
  @KType(SaleDetailEntity)
  details: SaleDetailEntity[] = []

  @KType(CustomerEntity)
  @KField({ label: '销售客户' })
  customer!: CustomerEntity

  // ── 基类（`BaseEntity` / `AbstractBaseBillEntity`）的字段在 K 注册表里没有条目，
  //    `declare` 重声明只为挂 `@K*`：K 列表要显示创建时间，K 详情要能看到驳回原因。
  @KTable({ order: -90, width: 170, datetime: true })
  @KField({ label: '创建时间' })
  declare createTime: number

  @KTable({ order: -91, width: 170, datetime: true, hide: true })
  @KField({ label: '更新时间' })
  declare updateTime: number

  @KField({ label: '驳回原因' })
  declare rejectReason: string

  getAuditingStatus(): WebEnum {
    return SaleStatusEnum.AUDITING
  }

  getAuditedStatus(): WebEnum {
    return SaleStatusEnum.OUTPUTTING
  }

  getRejectedStatus(): WebEnum {
    return SaleStatusEnum.REJECTED
  }
}

/** 客户是嵌套对象（列表列渲染器读 `row.customer.*`） */
function customerPart(row: unknown, part: 'code' | 'name'): string {
  const customer = (row as { customer?: Record<string, string> }).customer
  return customer?.[part] ?? '-'
}
