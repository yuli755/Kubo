import type { Ref } from 'vue'
import type { MaterialEntity, MaterialQuery } from '@/api/asset/types'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, unref } from 'vue'
import { materialApi } from '@/api/asset'

export function useMaterialList(query: Ref<MaterialQuery> | MaterialQuery) {
  const resolvedQuery = computed(() => unref(query))
  return useQuery<{ list: MaterialEntity[], total: number }>({
    queryKey: ['asset:material', 'list', resolvedQuery],
    queryFn: () => materialApi.getPage(resolvedQuery.value),
    placeholderData: keepPreviousData,
  })
}

export function useMaterialDetail(id: Ref<number | undefined> | number | undefined) {
  return useQuery<MaterialEntity>({
    queryKey: ['asset:material', 'detail', id],
    queryFn: () => materialApi.getDetail(unref(id)!),
    enabled: () => unref(id) !== undefined && unref(id) !== null,
  })
}

export function useMaterialDelete() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => materialApi.delete(id),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['asset:material'] }) },
  })
}
