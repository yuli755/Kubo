import type { Ref } from 'vue'
import type { InventoryEntity, InventoryQuery } from '@/api/wms/types'
import { keepPreviousData, useQuery } from '@tanstack/vue-query'
import { computed, unref } from 'vue'
import { inventoryApi } from '@/api/wms'

// F3 数据层收敛（D5）：视图层禁止直接 import `@/model/**Service`。
// 库存的 CRUD 走 airpower 的 Service 类，由本 hook 模块统一转发。
export { InventoryService } from '@/model/wms/inventory/InventoryService'

/**
 * # WMS 库存列表查询 Hook
 *
 * 基于 @tanstack/vue-query，支持分页参数响应式变更。
 *
 * @example
 * ```ts
 * const query = ref<InventoryQuery>({ page: 1, pageSize: 20 })
 * const { data, isLoading } = useInventoryList(query)
 * ```
 */
export function useInventoryList(query: Ref<InventoryQuery> | InventoryQuery) {
  const resolvedQuery = computed(() => unref(query))

  return useQuery<{ list: InventoryEntity[], total: number }>({
    queryKey: ['wms:inventory', 'list', resolvedQuery],
    queryFn: () => inventoryApi.getPage(resolvedQuery.value),
    placeholderData: keepPreviousData,
  })
}

/**
 * # WMS 库存详情查询 Hook
 *
 * @example
 * ```ts
 * const { data, isLoading } = useInventoryDetail(1)
 * ```
 */
export function useInventoryDetail(id: Ref<number | undefined> | number | undefined) {
  return useQuery<InventoryEntity>({
    queryKey: ['wms:inventory', 'detail', id],
    queryFn: () => inventoryApi.getDetail(unref(id)!),
    enabled: () => unref(id) !== undefined && unref(id) !== null,
  })
}
