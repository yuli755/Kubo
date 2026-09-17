import { inputApi } from './input'
import { moveApi } from './move'
import { outputApi } from './output'

export { inputApi, moveApi, outputApi }

/**
 * # WMS 模块 API 统一入口
 *
 * 汇总 WMS 各子模块的 API 对象，供外部统一导入。
 *
 * @example
 * ```ts
 * import { wmsApi } from '@/api/wms'
 * const list = await wmsApi.inventory.getPage({ page: 1, pageSize: 20 })
 * ```
 */
