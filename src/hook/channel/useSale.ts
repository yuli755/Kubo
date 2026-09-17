import type { BillAuditRequest, BillRejectRequest } from '@/api/channel/types'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { saleApi } from '@/api/channel'

// F3 数据层收敛（D5）：视图层禁止直接 import `@/model/**Service`。
// 销售单及其明细的 CRUD 走 airpower 的 Service 类，由本 hook 模块统一转发。
export { SaleDetailService } from '@/model/channel/sale/SaleDetailService'
export { SaleService } from '@/model/channel/sale/SaleService'

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
