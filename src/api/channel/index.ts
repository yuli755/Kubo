import { customerApi } from './customer'
import { purchaseApi } from './purchase'
import { purchasePriceApi } from './purchasePrice'
import { saleApi } from './sale'
import { salePriceApi } from './salePrice'
import { supplierApi } from './supplier'

export { customerApi, purchaseApi, purchasePriceApi, saleApi, salePriceApi, supplierApi }

export const channelApi = {
  customer: customerApi,
  purchase: purchaseApi,
  purchasePrice: purchasePriceApi,
  sale: saleApi,
  salePrice: salePriceApi,
  supplier: supplierApi,
}