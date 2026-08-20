import type { Ref } from 'vue'
import type { BillAuditRequest, BillFinishRequest, BillRejectRequest, PurchaseEntity, PurchaseQuery } from '@/api/channel/types'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, unref } from 'vue'
import { purchaseApi } from '@/api/channel'

export function usePurchaseList(query: Ref<PurchaseQuery> | PurchaseQuery) {
  const resolvedQuery = computed(() => unref(query))
  return useQuery<{ list: PurchaseEntity[], total: number }>({
    queryKey: ['channel:purchase', 'list', resolvedQuery],
    queryFn: () => purchaseApi.getPage(resolvedQuery.value),
    placeholderData: keepPreviousData,
  })
}

export function usePurchaseDetail(id: Ref<number | undefined> | number | undefined) {
  return useQuery<PurchaseEntity>({
    queryKey: ['channel:purchase', 'detail', id],
    queryFn: () => purchaseApi.getDetail(unref(id)!),
    enabled: () => unref(id) !== undefined && unref(id) !== null,
  })
}

export function usePurchaseAudit() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: BillAuditRequest) => purchaseApi.audit(data),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['channel:purchase'] }) },
  })
}

export function usePurchaseReject() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: BillRejectRequest) => purchaseApi.reject(data),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['channel:purchase'] }) },
  })
}

export function usePurchaseFinish() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: BillFinishRequest) => purchaseApi.setBillDetailsAllFinished(data),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['channel:purchase'] }) },
  })
}