import type { BillAuditRequest, BillRejectRequest } from '@/api/channel/types'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { purchaseApi } from '@/api/channel'

// F3 数据层收敛（D5）：视图层禁止直接 import `@/model/**Service`。
// 采购单及其明细的 CRUD 走 airpower 的 Service 类，由本 hook 模块统一转发。
export { PurchaseDetailService } from '@/model/channel/purchase/PurchaseDetailService'
export { PurchaseService } from '@/model/channel/purchase/PurchaseService'

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
