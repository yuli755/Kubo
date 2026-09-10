import { AbstractBaseService } from '@/base/AbstractBaseService'
import { MaterialEntity } from '@/model/asset/material/MaterialEntity'
import { reportError } from '@/utils/errorHandler'
import { CustomerEntity } from '../customer/CustomerEntity'
import { SalePriceEntity } from './SalePriceEntity'

export class SalePriceService extends AbstractBaseService<SalePriceEntity> {
  entityClass = SalePriceEntity

  baseUrl = 'salePrice'

  async getByMaterialAndCustomer(materialId: number, customerId: number): Promise<SalePriceEntity | null> {
    const entity = new SalePriceEntity()
    entity.material = new MaterialEntity(materialId)
    entity.customer = new CustomerEntity(customerId)
    try {
      return this.api('getByMaterialAndCustomer').throwError().requestModel(entity, SalePriceEntity)
    }
    catch (e) {
      reportError(e, 'sale-price')
      return null
    }
  }
}
