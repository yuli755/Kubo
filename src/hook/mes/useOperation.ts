import type { Ref } from 'vue'
import type { OperationEntity, OperationQuery } from '@/api/mes/types'
import { keepPreviousData, useQuery } from '@tanstack/vue-query'
import { computed, unref } from 'vue'
import { operationApi } from '@/api/mes'

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
