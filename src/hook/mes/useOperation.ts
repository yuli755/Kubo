import type { Ref } from 'vue'
import type { OperationEntity, OperationQuery } from '@/api/mes/types'
import { keepPreviousData, useQuery } from '@tanstack/vue-query'
import { computed, unref } from 'vue'
import { operationApi } from '@/api/mes'

// F3 数据层收敛（D5）：视图层禁止直接 import `@/model/**Service`。
// 工序的 CRUD 走 airpower 的 Service 类，由本 hook 模块统一转发。
export { OperationService } from '@/model/mes/operation/OperationService'

/**
 * # MES 工序列表查询 Hook
 *
 * @example
 * ```ts
 * const query = ref<OperationQuery>({ page: 1, pageSize: 20 })
 * const { data, isFetching, refetch } = useOperationList(query)
 * ```
 */
export function useOperationList(query: Ref<OperationQuery> | OperationQuery) {
  const resolvedQuery = computed(() => unref(query))

  return useQuery<{ list: OperationEntity[], total: number }>({
    queryKey: ['mes:operation', 'list', resolvedQuery],
    queryFn: () => operationApi.getPage(resolvedQuery.value),
    placeholderData: keepPreviousData,
  })
}

/**
 * # MES 工序详情查询 Hook
 *
 * @example
 * ```ts
 * const { data, isLoading } = useOperationDetail(1)
 * ```
 */
export function useOperationDetail(id: Ref<number | undefined> | number | undefined) {
  return useQuery<OperationEntity>({
    queryKey: ['mes:operation', 'detail', id],
    queryFn: () => operationApi.getDetail(unref(id)!),
    enabled: () => unref(id) !== undefined && unref(id) !== null,
  })
}
