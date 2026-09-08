import type { Ref } from 'vue'
import type { BillAuditRequest, BillFinishRequest, BillRejectRequest, PurchaseEntity, PurchaseQuery } from '@/api/channel/types'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, unref } from 'vue'
import { purchaseApi } from '@/api/channel'

// F3 数据层收敛（D5）：视图层禁止直接 import `@/model/**Service`。
// 采购单及其明细的 CRUD 走 airpower 的 Service 类，由本 hook 模块统一转发。
export { PurchaseDetailService } from '@/model/channel/purchase/PurchaseDetailService'
export { PurchaseService } from '@/model/channel/purchase/PurchaseService'

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
