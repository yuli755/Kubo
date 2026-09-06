import type { Ref } from 'vue'
import type { PurchasePriceEntity, PurchasePriceQuery } from '@/api/channel/types'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, unref } from 'vue'
import { purchasePriceApi } from '@/api/channel'

export function usePurchasePriceList(query: Ref<PurchasePriceQuery> | PurchasePriceQuery) {
  const resolvedQuery = computed(() => unref(query))
  return useQuery<{ list: PurchasePriceEntity[], total: number }>({
    queryKey: ['channel:purchasePrice', 'list', resolvedQuery],
    queryFn: () => purchasePriceApi.getPage(resolvedQuery.value),
    placeholderData: keepPreviousData,
  })
}

export function usePurchasePriceDetail(id: Ref<number | undefined> | number | undefined) {
  return useQuery<PurchasePriceEntity>({
    queryKey: ['channel:purchasePrice', 'detail', id],
    queryFn: () => purchasePriceApi.getDetail(unref(id)!),
    enabled: () => unref(id) !== undefined && unref(id) !== null,
  })
}

export function usePurchasePriceDelete() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => purchasePriceApi.delete(id),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['channel:purchasePrice'] }) },
  })
}
