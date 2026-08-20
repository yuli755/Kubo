import type { Ref } from 'vue'
import type { SalePriceEntity, SalePriceQuery } from '@/api/channel/types'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, unref } from 'vue'
import { salePriceApi } from '@/api/channel'

export function useSalePriceList(query: Ref<SalePriceQuery> | SalePriceQuery) {
  const resolvedQuery = computed(() => unref(query))
  return useQuery<{ list: SalePriceEntity[], total: number }>({
    queryKey: ['channel:salePrice', 'list', resolvedQuery],
    queryFn: () => salePriceApi.getPage(resolvedQuery.value),
    placeholderData: keepPreviousData,
  })
}

export function useSalePriceDetail(id: Ref<number | undefined> | number | undefined) {
  return useQuery<SalePriceEntity>({
    queryKey: ['channel:salePrice', 'detail', id],
    queryFn: () => salePriceApi.getDetail(unref(id)!),
    enabled: () => unref(id) !== undefined && unref(id) !== null,
  })
}

export function useSalePriceDelete() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => salePriceApi.delete(id),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['channel:salePrice'] }) },
  })
}