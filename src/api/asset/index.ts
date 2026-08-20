export { contractApi } from './contract'
export { deviceApi } from './device'
export { materialApi } from './material'

export const assetApi = {
  contract: contractApi,
  device: deviceApi,
  material: materialApi,
}