import { AbstractBaseService } from '@/base/AbstractBaseService'
import { MaterialEntity } from '@/model/asset/material/MaterialEntity'
import { reportError } from '@/utils/errorHandler'
import { SupplierEntity } from '../supplier/SupplierEntity'
import { PurchasePriceEntity } from './PurchasePriceEntity'

export class PurchasePriceService extends AbstractBaseService<PurchasePriceEntity> {
  entityClass = PurchasePriceEntity

  baseUrl = 'purchasePrice'

  async getByMaterialAndSupplier(materialId: number, supplierId: number): Promise<PurchasePriceEntity | null> {
    const entity = new PurchasePriceEntity()
    entity.material = new MaterialEntity(materialId)
    entity.supplier = new SupplierEntity(supplierId)
    try {
      return this.api('getByMaterialAndSupplier').throwError().requestModel(entity, PurchasePriceEntity)
    }
    catch (e) {
      reportError(e, 'purchase-price')
      return null
    }
  }
}
