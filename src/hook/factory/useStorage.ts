// 新底座服务（F2 起的页面用）：同样是"视图只从 hook 取"的约定，避免视图层直接依赖 @/model
export { KStorageService } from '@/model/factory/storage/KStorageService'
// F3 数据层收敛（D5）：视图层禁止直接 import `@/model/**Service`，服务统一从这里取。
// 批 4 把 wms 入库/移库的仓库选择换到 `KSelector` 后，旧 `StorageService`（airpower 基类）
// 与 `factory/storage/component/selector.vue` 都已无消费者、随之删除。

/**
 * # Factory 仓库树数据查询 Hook
 *
 * 仓库为树形实体（ITree），使用 `getList` 获取全量树数据，无需分页。
 *
 * @example
 * ```ts
 * const { data, isFetching } = useStorageTree()
 * ```
 */

/**
 * # Factory 仓库详情查询 Hook
 *
 * @example
 * ```ts
 * const { data, isLoading } = useStorageDetail(1)
 * ```
 */

/**
 * # Factory 仓库发布 Mutation
 *
 * 发布成功后自动失效树数据缓存。
 */
