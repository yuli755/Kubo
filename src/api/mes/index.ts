export { bomApi } from './bom'
export { operationApi } from './operation'
export { orderApi } from './order'
export { pickingApi } from './picking'
export { planApi } from './plan'
export { routingApi } from './routing'

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
