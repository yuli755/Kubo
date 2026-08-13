import type { Ref } from 'vue'
import type { BillAuditRequest, BillFinishRequest, BillRejectRequest, InputEntity, InputQuery } from '@/api/wms/types'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, unref } from 'vue'
import { inputApi } from '@/api/wms'

/**
 * # WMS 入库单列表查询 Hook
 */
export function useInputList(query: Ref<InputQuery> | InputQuery) {
  const resolvedQuery = computed(() => unref(query))

  return useQuery<{ list: InputEntity[], total: number }>({
    queryKey: ['wms:input', 'list', resolvedQuery],
    queryFn: () => inputApi.getPage(resolvedQuery.value),
    placeholderData: keepPreviousData,
  })
}

/**
 * # WMS 入库单详情查询 Hook
 */
export function useInputDetail(id: Ref<number | undefined> | number | undefined) {
  return useQuery<InputEntity>({
    queryKey: ['wms:input', 'detail', id],
    queryFn: () => inputApi.getDetail(unref(id)!),
    enabled: () => unref(id) !== undefined && unref(id) !== null,
  })
}

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
export function useInputFinish() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: BillFinishRequest) => inputApi.setBillDetailsAllFinished(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['wms:input'] })
    },
  })
}

/**
 * # WMS 入库单添加完成数量 Mutation
 *
 * 对应后端 `BaseBillController.addFinish`（`/input/addFinish`）。
 * 当前明细视图仍由 `useBillDetail` 串接 airpower `InputService.addDetailFinishQuantity`，
 * 本 hook 作为新 API 层封装预留，便于后续迁移。
 */
export function useInputAddFinish() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: { id: number, finishQuantity: number }) => inputApi.addDetailFinishQuantity(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['wms:input'] })
    },
  })
}
