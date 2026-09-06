import type { Ref } from 'vue'
import type { UnitEntity, UnitQuery } from '@/api/system/types'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, unref } from 'vue'
import { unitApi } from '@/api/system'

/**
 * # System 计量单位列表查询 Hook
 */
export function useUnitList(query: Ref<UnitQuery> | UnitQuery) {
  const resolvedQuery = computed(() => unref(query))
  return useQuery<{ list: UnitEntity[], total: number }>({
    queryKey: ['system:unit', 'list', resolvedQuery],
    queryFn: () => unitApi.getPage(resolvedQuery.value),
    placeholderData: keepPreviousData,
  })
}

/**
 * # System 计量单位详情查询 Hook
 */
export function useUnitDetail(id: Ref<number | undefined> | number | undefined) {
  return useQuery<UnitEntity>({
    queryKey: ['system:unit', 'detail', id],
    queryFn: () => unitApi.getDetail(unref(id)!),
    enabled: () => unref(id) !== undefined && unref(id) !== null,
  })
}

/**
 * # System 计量单位删除 Mutation Hook
 */
export function useUnitDelete() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => unitApi.delete(id),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['system:unit'] }) },
  })
}
