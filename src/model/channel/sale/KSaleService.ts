import type { SaleDetailEntity } from './SaleDetailEntity'
import { AbstractBillService } from '@/framework'
import { SaleEntity } from './SaleEntity'

/**
 * # 销售单服务（新底座）
 *
 * 与采购单同一套单据动作；「作业」载荷同样是明细、数量为**本次**出库数量。
 */
export class KSaleService extends AbstractBillService<SaleEntity, SaleDetailEntity> {
  entityClass = SaleEntity
  baseUrl = 'sale'
}
