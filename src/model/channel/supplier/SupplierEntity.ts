import type { IPayload } from '@/framework'
import { BaseEntity } from '@/base/BaseEntity'
import { DesensitizeType as KDesensitizeType, Field as KField, Form as KForm, Model as KModel, Search as KSearch, Table as KTable } from '@/framework'

/**
 * # 供应商实体（F4 批 5 起双注册）
 *
 * K 侧（`@K*`）服务于采购价与采购单里的供应商选择与列渲染；
 * airpower 那套暂时保留 —— 系统域（批 6）里还有页面在读旧注册表。
 * 收尾（F5）时删掉 airpower 装饰器并切到 `KEntity`。
 */
@KModel({
  label: '供应商',
})
export class SupplierEntity extends BaseEntity implements IPayload {
  /**
   * ### 供应商名称
   */
  @KTable({ order: 100, minWidth: 200, force: true, copy: true, sortable: true })
  @KForm({ requiredString: true, maxLength: 64 })
  @KField({ label: '供应商名称' })
  @KSearch()
  name!: string

  /**
   * ### 供应商编码
   */
  @KTable({ order: 90, minWidth: 160, force: true, copy: true })
  @KForm({ placeholder: '不输入按编码规则自动生成', maxLength: 32 })
  @KField({ label: '供应商编码' })
  @KSearch()
  code!: string

  /**
   * ### 联系电话
   */
  @KTable({ order: 80, minWidth: 150, phone: true, desensitize: KDesensitizeType.MOBILE })
  @KForm({ phone: true, maxLength: 20 })
  @KField({ label: '联系电话' })
  @KSearch()
  phone!: string

  getPayloadLabel(): string {
    return this.name
  }
}
