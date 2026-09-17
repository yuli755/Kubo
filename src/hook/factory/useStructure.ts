// 新底座服务（F2 起的页面用）
export { KStructureService } from '@/model/factory/structure/KStructureService'
// F3 数据层收敛（D5）：视图层禁止直接 import `@/model/**Service`，统一从这里取。
// 批 4 起库存选择器改用 K 服务，旧 `StructureService`（airpower 基类）已无消费者、
// 随之删除 —— 生产单元域同样只剩一份实现。

/**
 * # Factory 生产单元树数据查询 Hook
 *
 * 生产单元为树形实体（ITree），使用 `getList` 获取全量树数据，无需分页。
 *
 * @example
 * ```ts
 * const { data, isFetching } = useStructureTree()
 * ```
 */

/**
 * # Factory 生产单元详情查询 Hook
 *
 * @example
 * ```ts
 * const { data, isLoading } = useStructureDetail(1)
 * ```
 */

/**
 * # Factory 生产单元发布 Mutation
 *
 * 发布成功后自动失效树数据缓存。
 */
