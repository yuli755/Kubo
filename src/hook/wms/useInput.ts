import type { BillAuditRequest, BillRejectRequest } from '@/api/wms/types'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { inputApi } from '@/api/wms'

// F3 数据层收敛（D5）：视图层禁止直接 import `@/model/**Service`。
// 入库单/明细的 CRUD 走 airpower 的 Service 类（useTanStackTable / useEditor / createValidator 需要类），
// 因此由本 hook 模块统一转发 —— 视图从这里取，不再回头依赖 @/model。
export { InputDetailService } from '@/model/wms/input/InputDetailService'
export { InputService } from '@/model/wms/input/InputService'

/**
 * # WMS 入库单列表查询 Hook
 */

/**
 * # WMS 入库单详情查询 Hook
 */

/**
 * # WMS 入库单审核 Mutation
 */
export function useInputAudit() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: BillAuditRequest) => inputApi.audit(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['wms:input'] })
    },
  })
}

/**
 * # WMS 入库单驳回 Mutation
 */
export function useInputReject() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: BillRejectRequest) => inputApi.reject(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['wms:input'] })
    },
  })
}

/**
 * # WMS 入库单完成明细 Mutation
 */

/**
 * # WMS 入库单添加完成数量 Mutation
 *
 * 对应后端 `BaseBillController.addFinish`（`/input/addFinish`）。
 * 阶段 E-H1：入库确认编辑器（InputAddFinishEditor）使用本 hook，
 * 载荷须含后端业务校验所需的 `storage.id` 与本次 `quantity`。
 */
export function useInputAddFinish() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: { id: number, quantity: number, storage: { id: number } }) => inputApi.addDetailFinishQuantity(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['wms:input'] })
    },
  })
}
