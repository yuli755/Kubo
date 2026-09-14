import type { Ref } from 'vue'
import type { BillAuditRequest, BillFinishRequest, BillRejectRequest, OutputEntity, OutputQuery } from '@/api/wms/types'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, unref } from 'vue'
import { outputApi } from '@/api/wms'

// F3 数据层收敛（D5）：视图层禁止直接 import `@/model/**Service`。
// 出库单/明细的 CRUD 走 airpower 的 Service 类（useTanStackTable / useEditor / createValidator 需要类），
// 因此由本 hook 模块统一转发 —— 视图从这里取，不再回头依赖 @/model。
export { OutputDetailService } from '@/model/wms/output/OutputDetailService'
export { OutputService } from '@/model/wms/output/OutputService'

/**
 * # WMS 出库单列表查询 Hook
 */
export function useOutputList(query: Ref<OutputQuery> | OutputQuery) {
  const resolvedQuery = computed(() => unref(query))

  return useQuery<{ list: OutputEntity[], total: number }>({
    queryKey: ['wms:output', 'list', resolvedQuery],
    queryFn: () => outputApi.getPage(resolvedQuery.value),
    placeholderData: keepPreviousData,
  })
}

/**
 * # WMS 出库单详情查询 Hook
 */
export function useOutputDetail(id: Ref<number | undefined> | number | undefined) {
  return useQuery<OutputEntity>({
    queryKey: ['wms:output', 'detail', id],
    queryFn: () => outputApi.getDetail(unref(id)!),
    enabled: () => unref(id) !== undefined && unref(id) !== null,
  })
}

/**
 * # WMS 出库单审核 Mutation
 */
export function useOutputAudit() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: BillAuditRequest) => outputApi.audit(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['wms:output'] })
    },
  })
}

/**
 * # WMS 出库单驳回 Mutation
 */
export function useOutputReject() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: BillRejectRequest) => outputApi.reject(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['wms:output'] })
    },
  })
}

/**
 * # WMS 出库单完成明细 Mutation
 */
export function useOutputFinish() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: BillFinishRequest) => outputApi.setBillDetailsAllFinished(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['wms:output'] })
    },
  })
}

/**
 * # WMS 出库单添加完成数量 Mutation
 *
 * 对应后端 `BaseBillController.addFinish`（`/output/addFinish`）。
 * 阶段 E-H1：出库确认编辑器（OutputAddFinishEditor）使用本 hook，
 * 载荷须含后端业务校验所需的 `inventory.id` 与本次 `quantity`。
 */
export function useOutputAddFinish() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: { id: number, quantity: number, inventory: { id: number } }) => outputApi.addDetailFinishQuantity(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['wms:output'] })
    },
  })
}
