import { AbstractCurdService, fill } from '@/framework'
import { reportError } from '@/utils/errorHandler'
import { SalePriceEntity } from './SalePriceEntity'

/**
 * # 销售价服务（新底座）
 *
 * 与采购价同构：多一个「按物料 + 客户查专属报价」的端点，
 * 入参同样是实体（`{ material: { id }, customer: { id } }`）。
 */
export class KSalePriceService extends AbstractCurdService<SalePriceEntity> {
  entityClass = SalePriceEntity
  baseUrl = 'salePrice'

  /** 查询某物料在某客户下的专属销售价；没有配置时返回 `null` */
  async getByMaterialAndCustomer(materialId: number, customerId: number): Promise<SalePriceEntity | null> {
    try {
      const data = await this.api('getByMaterialAndCustomer').requestRaw<unknown>({
        material: { id: materialId },
        customer: { id: customerId },
      })
      return data ? fill(new SalePriceEntity(), data) : null
    }
    catch (error) {
      reportError(error, 'sale-price')
      return null
    }
  }
}
