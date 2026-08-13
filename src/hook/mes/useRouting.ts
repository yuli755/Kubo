import type { Ref } from 'vue'
import type { RoutingEntity, RoutingQuery } from '@/api/mes/types'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, unref } from 'vue'
import { routingApi } from '@/api/mes'

/**
 * # MES 生产工艺列表查询 Hook
 *
 * @example
 * ```ts
 * const query = ref<RoutingQuery>({ page: 1, pageSize: 20 })
 * const { data, isFetching, refetch } = useRoutingList(query)
 * ```
 */
export function useRoutingList(query: Ref<RoutingQuery> | RoutingQuery) {
  const resolvedQuery = computed(() => unref(query))

  return useQuery<{ list: RoutingEntity[], total: number }>({
    queryKey: ['mes:routing', 'list', resolvedQuery],
    queryFn: () => routingApi.getPage(resolvedQuery.value),
    placeholderData: keepPreviousData,
  })
}

/**
 * # MES 生产工艺详情查询 Hook
 *
 * @example
 * ```ts
 * const { data, isLoading } = useRoutingDetail(1)
 * ```
 */
export function useRoutingDetail(id: Ref<number | undefined> | number | undefined) {
  return useQuery<RoutingEntity>({
    queryKey: ['mes:routing', 'detail', id],
    queryFn: () => routingApi.getDetail(unref(id)!),
    enabled: () => unref(id) !== undefined && unref(id) !== null,
  })
}

/**
 * # MES 生产工艺发布 Mutation
 */
export function useRoutingPublish() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: { id: number }) => routingApi.publish(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['mes:routing'] })
    },
  })
}
