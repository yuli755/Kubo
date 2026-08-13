import type { Ref } from 'vue'
import type { BillAuditRequest, BillFinishRequest, BillRejectRequest, OutputEntity, OutputQuery } from '@/api/wms/types'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, unref } from 'vue'
import { outputApi } from '@/api/wms'

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
 * 当前明细视图仍由 `useBillDetail` 串接 airpower `OutputService.addDetailFinishQuantity`，
 * 本 hook 作为新 API 层封装预留，便于后续迁移。
 */
export function useOutputAddFinish() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: { id: number, finishQuantity: number }) => outputApi.addDetailFinishQuantity(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['wms:output'] })
    },
  })
}
