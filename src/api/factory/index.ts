import { storageApi } from './storage'
import { structureApi } from './structure'

export { storageApi, structureApi }

/**
 * # Factory 模块 API 统一入口
 *
 * 汇总 Factory 各子模块的 API 对象，供外部统一导入。
 *
 * @example
 * ```ts
 * import { factoryApi } from '@/api/factory'
 * const list = await factoryApi.storage.getList()
 * ```
 */
export const factoryApi = {
  storage: storageApi,
  structure: structureApi,
}
