import type { Ref } from 'vue'
import type { BillAuditRequest, BillFinishRequest, BillRejectRequest, PickingEntity, PickingQuery } from '@/api/mes/types'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, unref } from 'vue'
import { pickingApi } from '@/api/mes'

// F3 数据层收敛（D5）：视图层禁止直接 import `@/model/**Service`。
// 领料单及其明细的 CRUD 走 airpower 的 Service 类，由本 hook 模块统一转发。
export { PickingDetailService } from '@/model/mes/picking/PickingDetailService'
export { PickingService } from '@/model/mes/picking/PickingService'

/**
 * # MES 领料单列表查询 Hook
 *
 * @example
 * ```ts
 * const query = ref<PickingQuery>({ page: 1, pageSize: 20 })
 * const { data, isFetching, refetch } = usePickingList(query)
 * ```
 */
export function usePickingList(query: Ref<PickingQuery> | PickingQuery) {
  const resolvedQuery = computed(() => unref(query))

  return useQuery<{ list: PickingEntity[], total: number }>({
    queryKey: ['mes:picking', 'list', resolvedQuery],
    queryFn: () => pickingApi.getPage(resolvedQuery.value),
    placeholderData: keepPreviousData,
  })
}

/**
 * # MES 领料单详情查询 Hook
 *
 * @example
 * ```ts
 * const { data, isLoading } = usePickingDetail(1)
 * ```
 */
export function usePickingDetail(id: Ref<number | undefined> | number | undefined) {
  return useQuery<PickingEntity>({
    queryKey: ['mes:picking', 'detail', id],
    queryFn: () => pickingApi.getDetail(unref(id)!),
    enabled: () => unref(id) !== undefined && unref(id) !== null,
  })
}

/**
 * # MES 领料单审核 Mutation
 */
export function usePickingAudit() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: BillAuditRequest) => pickingApi.audit(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['mes:picking'] })
    },
  })
}

/**
 * # MES 领料单驳回 Mutation
 */
export function usePickingReject() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: BillRejectRequest) => pickingApi.reject(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['mes:picking'] })
    },
  })
}

/**
 * # MES 领料单完成明细 Mutation
 */
export function usePickingFinish() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: BillFinishRequest) => pickingApi.setBillDetailsAllFinished(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['mes:picking'] })
    },
  })
}
