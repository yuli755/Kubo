import type { Ref } from 'vue'
import type { BomEntity, BomQuery } from '@/api/mes/types'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, unref } from 'vue'
import { bomApi } from '@/api/mes'

/**
 * # MES 生产配方列表查询 Hook
 *
 * @example
 * ```ts
 * const query = ref<BomQuery>({ page: 1, pageSize: 20 })
 * const { data, isFetching, refetch } = useBomList(query)
 * ```
 */
export function useBomList(query: Ref<BomQuery> | BomQuery) {
  const resolvedQuery = computed(() => unref(query))

  return useQuery<{ list: BomEntity[], total: number }>({
    queryKey: ['mes:bom', 'list', resolvedQuery],
    queryFn: () => bomApi.getPage(resolvedQuery.value),
    placeholderData: keepPreviousData,
  })
}

/**
 * # MES 生产配方详情查询 Hook
 *
 * @example
 * ```ts
 * const { data, isLoading } = useBomDetail(1)
 * ```
 */
export function useBomDetail(id: Ref<number | undefined> | number | undefined) {
  return useQuery<BomEntity>({
    queryKey: ['mes:bom', 'detail', id],
    queryFn: () => bomApi.getDetail(unref(id)!),
    enabled: () => unref(id) !== undefined && unref(id) !== null,
  })
}

/**
 * # MES 生产配方发布 Mutation
 */
export function useBomPublish() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: { id: number }) => bomApi.publish(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['mes:bom'] })
    },
  })
}
