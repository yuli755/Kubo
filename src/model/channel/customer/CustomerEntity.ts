import type { IPayload } from '@/framework'
import { BaseEntity } from '@/base/BaseEntity'
import { DesensitizeType as KDesensitizeType, Field as KField, Form as KForm, Model as KModel, Search as KSearch, Table as KTable } from '@/framework'

/**
 * # 客户实体（F4 批 5 起双注册）
 *
 * K 侧（`@K*`）服务于采购价/销售价与销售单里的客户选择与列渲染；
 * airpower 那套暂时保留 —— 系统域（批 6）里还有页面在读旧注册表。
 * 收尾（F5）时删掉 airpower 装饰器并切到 `KEntity`。
 */
@KModel({
  label: '客户',
})
export class CustomerEntity extends BaseEntity implements IPayload {
  /**
   * ### 客户名称
   */
  @KTable({ order: 100, minWidth: 180, force: true, copy: true, sortable: true })
  @KForm({ requiredString: true, maxLength: 64 })
  @KField({ label: '客户名称' })
  @KSearch()
  name!: string

  /**
   * ### 客户编码
   *
   * 定宽 180（实测 75px）。原 `minWidth: 160` 让三列一起瓜分剩余宽度，
   * 实测名称 372 / 编码 296 / 电话 278，内容只有 56 / 75 / 82px。列宽口径见 `src/framework/types.ts`。
   */
  @KTable({ order: 90, width: 180, force: true, copy: true })
  @KForm({ placeholder: '不输入按编码规则自动生成', maxLength: 32 })
  @KField({ label: '客户编码' })
  @KSearch()
  code!: string

  /**
   * ### 联系电话
   *
   * 定宽 150：脱敏后固定 11 位（实测 82px），加宽只会留白。
   */
  @KTable({ order: 80, width: 150, phone: true, desensitize: KDesensitizeType.MOBILE })
  @KForm({ phone: true, maxLength: 20 })
  @KField({ label: '联系电话' })
  @KSearch()
  phone!: string

  /** 字典/基础数据表统一补「创建时间」列，理由同 `StorageEntity.createTime`。 */
  @KTable({ order: -90, width: 170, datetime: true })
  @KField({ label: '创建时间' })
  declare createTime: number

  getPayloadLabel(): string {
    return this.name
  }
}
