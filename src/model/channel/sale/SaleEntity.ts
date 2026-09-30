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
  @KTable({ order: 99, width: 150, force: true, copy: true })
  @KForm({ placeholder: '不填写按编码规则自动生成' })
  @KField({ label: '销售单号' })
  @KSearch()
  declare billCode: string

  /**
   * 列宽口径见 `src/framework/types.ts`。改前本页 `sum=1496` vs 可用 `1142`，横向溢出 354px，
   * 是全仓最严重的一页；四个文本列同时用 `minWidth` 平分剩余宽度，于是每列都宽一点、每列又都不够宽。
   * 改后只留「客户名称」一个弹性列，其余按内容定宽，Σ 可见列 = 850 ≤ 856（1142 - 56 序号 - 230 操作）。
   *
   * 「客户编码」默认 `hide`：它与「客户名称」是同一个 `CustomerEntity` 的两个投影，列表上并排两列
   * 只为多显示一个人类不读的内部编码。`hide` 只是布局取舍不是权限控制 —— 列选择器里能勾回来，
   * 详情抽屉与编辑器也都仍展示。
   */
  @KTable({
    order: 90,
    width: 140,
    hide: true,
    copy: true,
    formatter: (row: never) => customerPart(row, 'code'),
  })
  @KField({ label: '客户编码' })
  customerCode!: string

  @KTable({
    order: 85,
    minWidth: 160,
    copy: true,
    formatter: (row: never) => customerPart(row, 'name'),
  })
  @KField({ label: '客户名称' })
  customerName!: string

  @KForm({ requiredNumber: true, placeholder: '请选择销售客户' })
  @KField({ label: '客户' })
  customerId!: number

  @KTable({ width: 160, order: 60 })
  @KForm({ textarea: true, maxLength: 80 })
  @KField({ label: '销售说明' })
  reason!: string

  @KTable({ width: 120, order: 50, money: true, force: true, align: 'right' })
  @KForm({ number: true, precision: 2, suffixText: '元' })
  @KField({ label: '总金额' })
  totalPrice!: number

  @KTable({ width: 100, order: -80, force: true, color: true })
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
  @KTable({ order: -90, width: 160, datetime: true })
  @KField({ label: '创建时间' })
  declare createTime: number

  @KTable({ order: -91, width: 160, datetime: true, hide: true })
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
