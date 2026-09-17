import { AbstractCurdService, fill } from '@/framework'
import { reportError } from '@/utils/errorHandler'
import { PurchasePriceEntity } from './PurchasePriceEntity'

/**
 * # 采购价服务（新底座）
 *
 * 除 CRUD 外多一个业务端点：按「物料 + 供应商」查专属报价。
 *
 * ⚠️ 后端 `getByMaterialAndSupplier` 的入参是**实体**（`@Validated(WhenGetByMaterialAndSupplier)`
 * 校验 `material` / `supplier` 两个关联对象），不是两个 id —— 所以这里组装
 * `{ material: { id }, supplier: { id } }`，与旧 airpower 实现发送的载荷一致。
 *
 * 「查不到」不是错误：后端返回空，调用方回退到物料的参考报价（见采购明细编辑器）。
 * 因此这里吞掉异常并返回 `null`，只把异常上报给错误收集器。
 */
export class KPurchasePriceService extends AbstractCurdService<PurchasePriceEntity> {
  entityClass = PurchasePriceEntity
  baseUrl = 'purchasePrice'

  /** 查询某物料在某供应商下的专属采购价；没有配置时返回 `null` */
  async getByMaterialAndSupplier(materialId: number, supplierId: number): Promise<PurchasePriceEntity | null> {
    try {
      const data = await this.api('getByMaterialAndSupplier').requestRaw<unknown>({
        material: { id: materialId },
        supplier: { id: supplierId },
      })
      return data ? fill(new PurchasePriceEntity(), data) : null
    }
    catch (error) {
      reportError(error, 'purchase-price')
      return null
    }
  }
}
