import { contractApi } from './contract'
import { materialApi } from './material'

export { contractApi, materialApi }

export const assetApi = {
  contract: contractApi,
  material: materialApi,
}
