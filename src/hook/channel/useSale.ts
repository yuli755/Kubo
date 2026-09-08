import type { Ref } from 'vue'
import type { BillAuditRequest, BillFinishRequest, BillRejectRequest, SaleEntity, SaleQuery } from '@/api/channel/types'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, unref } from 'vue'
import { saleApi } from '@/api/channel'

// F3 数据层收敛（D5）：视图层禁止直接 import `@/model/**Service`。
// 销售单及其明细的 CRUD 走 airpower 的 Service 类，由本 hook 模块统一转发。
export { SaleDetailService } from '@/model/channel/sale/SaleDetailService'
export { SaleService } from '@/model/channel/sale/SaleService'

export function useSaleList(query: Ref<SaleQuery> | SaleQuery) {
  const resolvedQuery = computed(() => unref(query))
  return useQuery<{ list: SaleEntity[], total: number }>({
    queryKey: ['channel:sale', 'list', resolvedQuery],
    queryFn: () => saleApi.getPage(resolvedQuery.value),
    placeholderData: keepPreviousData,
  })
}

export function useSaleDetail(id: Ref<number | undefined> | number | undefined) {
  return useQuery<SaleEntity>({
    queryKey: ['channel:sale', 'detail', id],
    queryFn: () => saleApi.getDetail(unref(id)!),
    enabled: () => unref(id) !== undefined && unref(id) !== null,
  })
}

export function useSaleAudit() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: BillAuditRequest) => saleApi.audit(data),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['channel:sale'] }) },
  })
}

export function useSaleReject() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: BillRejectRequest) => saleApi.reject(data),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['channel:sale'] }) },
  })
}

export function useSaleFinish() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: BillFinishRequest) => saleApi.setBillDetailsAllFinished(data),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['channel:sale'] }) },
  })
}
