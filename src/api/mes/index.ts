import { bomApi } from './bom'
import { operationApi } from './operation'
import { orderApi } from './order'
import { pickingApi } from './picking'
import { planApi } from './plan'
import { routingApi } from './routing'

export { bomApi, operationApi, orderApi, pickingApi, planApi, routingApi }

/**
 * # MES 模块 API 统一入口
 *
 * 汇总 MES 各子模块的 API 对象，供外部统一导入。
 *
 * @example
 * ```ts
 * import { mesApi } from '@/api/mes'
 * const list = await mesApi.order.getPage({ page: 1, pageSize: 20 })
 * ```
 */
export const mesApi = {
  bom: bomApi,
  operation: operationApi,
  order: orderApi,
  picking: pickingApi,
  plan: planApi,
  routing: routingApi,
}
