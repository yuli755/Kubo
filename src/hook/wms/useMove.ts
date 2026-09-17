import type { BillAuditRequest, BillRejectRequest } from '@/api/wms/types'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { moveApi } from '@/api/wms'

// F3 数据层收敛（D5）：视图层禁止直接 import `@/model/**Service`。
// 移库单/明细的 CRUD 走 airpower 的 Service 类（useTanStackTable / useEditor / createValidator 需要类），
// 因此由本 hook 模块统一转发 —— 视图从这里取，不再回头依赖 @/model。
export { MoveDetailService } from '@/model/wms/move/MoveDetailService'
export { MoveService } from '@/model/wms/move/MoveService'

/**
 * # WMS 移库单列表查询 Hook
 */

/**
 * # WMS 移库单详情查询 Hook
 */

/**
 * # WMS 移库单审核 Mutation
 */
export function useMoveAudit() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: BillAuditRequest) => moveApi.audit(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['wms:move'] })
    },
  })
}

/**
 * # WMS 移库单驳回 Mutation
 */
export function useMoveReject() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: BillRejectRequest) => moveApi.reject(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['wms:move'] })
    },
  })
}

/**
 * # WMS 移库单完成明细 Mutation
 */

/**
 * # WMS 移库单添加完成数量 Mutation
 *
 * 对应后端 `BaseBillController.addFinish`（`/move/addFinish`）。
 * 阶段 E-H1：来源库存由后端按明细 ID 从库中读取，载荷只需 `id` 与本次 `quantity`。
 */
