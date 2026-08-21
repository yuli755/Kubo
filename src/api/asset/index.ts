import { contractApi } from './contract'
import { deviceApi } from './device'
import { materialApi } from './material'

export { contractApi, deviceApi, materialApi }

export const assetApi = {
  contract: contractApi,
  device: deviceApi,
  material: materialApi,
}