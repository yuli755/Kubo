import type { Ref } from 'vue'
import type { BillAuditRequest, BillFinishRequest, BillRejectRequest, PlanEntity, PlanQuery } from '@/api/mes/types'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, unref } from 'vue'
import { planApi } from '@/api/mes'

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
