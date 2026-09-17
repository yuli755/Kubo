import { AbstractCurdService } from '@/framework'
import { SupplierEntity } from './SupplierEntity'

/**
 * # 供应商服务（新底座）
 *
 * 与旧 `SupplierService` 同 `baseUrl`；采购单/采购价/供应商选择器都走这一份。
 */
export class KSupplierService extends AbstractCurdService<SupplierEntity> {
  entityClass = SupplierEntity
  baseUrl = 'supplier'
}
