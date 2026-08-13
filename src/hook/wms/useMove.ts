import type { Ref } from 'vue'
import type { BillAuditRequest, BillFinishRequest, BillRejectRequest, MoveEntity, MoveQuery } from '@/api/wms/types'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, unref } from 'vue'
import { moveApi } from '@/api/wms'

/**
 * # WMS 移库单列表查询 Hook
 */
export function useMoveList(query: Ref<MoveQuery> | MoveQuery) {
  const resolvedQuery = computed(() => unref(query))

  return useQuery<{ list: MoveEntity[], total: number }>({
    queryKey: ['wms:move', 'list', resolvedQuery],
    queryFn: () => moveApi.getPage(resolvedQuery.value),
    placeholderData: keepPreviousData,
  })
}

/**
 * # WMS 移库单详情查询 Hook
 */
export function useMoveDetail(id: Ref<number | undefined> | number | undefined) {
  return useQuery<MoveEntity>({
    queryKey: ['wms:move', 'detail', id],
    queryFn: () => moveApi.getDetail(unref(id)!),
    enabled: () => unref(id) !== undefined && unref(id) !== null,
  })
}

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
export function useMoveFinish() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: BillFinishRequest) => moveApi.setBillDetailsAllFinished(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['wms:move'] })
    },
  })
}

/**
 * # WMS 移库单添加完成数量 Mutation
 *
 * 对应后端 `BaseBillController.addFinish`（`/move/addFinish`）。
 * 当前明细视图仍由 `useBillDetail` 串接 airpower `MoveService.addDetailFinishQuantity`，
 * 本 hook 作为新 API 层封装预留，便于后续迁移。
 */
export function useMoveAddFinish() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: { id: number, finishQuantity: number }) => moveApi.addDetailFinishQuantity(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['wms:move'] })
    },
  })
}
