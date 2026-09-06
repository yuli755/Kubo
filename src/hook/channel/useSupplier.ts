import type { Ref } from 'vue'
import type { SupplierEntity, SupplierQuery } from '@/api/channel/types'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, unref } from 'vue'
import { supplierApi } from '@/api/channel'

export function useSupplierList(query: Ref<SupplierQuery> | SupplierQuery) {
  const resolvedQuery = computed(() => unref(query))
  return useQuery<{ list: SupplierEntity[], total: number }>({
    queryKey: ['channel:supplier', 'list', resolvedQuery],
    queryFn: () => supplierApi.getPage(resolvedQuery.value),
    placeholderData: keepPreviousData,
  })
}

export function useSupplierDetail(id: Ref<number | undefined> | number | undefined) {
  return useQuery<SupplierEntity>({
    queryKey: ['channel:supplier', 'detail', id],
    queryFn: () => supplierApi.getDetail(unref(id)!),
    enabled: () => unref(id) !== undefined && unref(id) !== null,
  })
}

export function useSupplierDelete() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => supplierApi.delete(id),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['channel:supplier'] }) },
  })
}
