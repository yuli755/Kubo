import type { Ref } from 'vue'
import type { BillAuditRequest, BillFinishRequest, BillRejectRequest, PlanEntity, PlanQuery } from '@/api/mes/types'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, unref } from 'vue'
import { planApi } from '@/api/mes'

// F3 数据层收敛（D5）：视图层禁止直接 import `@/model/**Service`。
// 生产计划及其明细的 CRUD 走 airpower 的 Service 类，由本 hook 模块统一转发。
export { PlanDetailService } from '@/model/mes/plan/PlanDetailService'
export { PlanService } from '@/model/mes/plan/PlanService'

/**
 * # MES 生产计划列表查询 Hook
 *
 * @example
 * ```ts
 * const query = ref<PlanQuery>({ page: 1, pageSize: 20 })
 * const { data, isFetching, refetch } = usePlanList(query)
 * ```
 */
export function usePlanList(query: Ref<PlanQuery> | PlanQuery) {
  const resolvedQuery = computed(() => unref(query))

  return useQuery<{ list: PlanEntity[], total: number }>({
    queryKey: ['mes:plan', 'list', resolvedQuery],
    queryFn: () => planApi.getPage(resolvedQuery.value),
    placeholderData: keepPreviousData,
  })
}

/**
 * # MES 生产计划详情查询 Hook
 *
 * @example
 * ```ts
 * const { data, isLoading } = usePlanDetail(1)
 * ```
 */
export function usePlanDetail(id: Ref<number | undefined> | number | undefined) {
  return useQuery<PlanEntity>({
    queryKey: ['mes:plan', 'detail', id],
    queryFn: () => planApi.getDetail(unref(id)!),
    enabled: () => unref(id) !== undefined && unref(id) !== null,
  })
}

/**
 * # MES 生产计划审核 Mutation
 */
export function usePlanAudit() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: BillAuditRequest) => planApi.audit(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['mes:plan'] })
    },
  })
}

/**
 * # MES 生产计划驳回 Mutation
 */
export function usePlanReject() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: BillRejectRequest) => planApi.reject(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['mes:plan'] })
    },
  })
}

/**
 * # MES 生产计划完成明细 Mutation
 */
export function usePlanFinish() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: BillFinishRequest) => planApi.setBillDetailsAllFinished(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['mes:plan'] })
    },
  })
}
