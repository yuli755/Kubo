export { customerApi } from './customer'
export { purchaseApi } from './purchase'
export { purchasePriceApi } from './purchasePrice'
export { saleApi } from './sale'
export { salePriceApi } from './salePrice'
export { supplierApi } from './supplier'

export const channelApi = {
  customer: customerApi,
  purchase: purchaseApi,
  purchasePrice: purchasePriceApi,
  sale: saleApi,
  salePrice: salePriceApi,
  supplier: supplierApi,
}