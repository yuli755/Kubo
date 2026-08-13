import type { Ref } from 'vue'
import type { BillAuditRequest, BillFinishRequest, BillRejectRequest, OrderEntity, OrderQuery, OrderReportRequest } from '@/api/mes/types'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, unref } from 'vue'
import { orderApi } from '@/api/mes'

/**
 * # MES 生产订单列表查询 Hook
 *
 * 基于 @tanstack/vue-query，支持分页参数响应式变更。
 *
 * @example
 * ```ts
 * const query = ref<OrderQuery>({ page: 1, pageSize: 20 })
 * const { data, isFetching, refetch } = useOrderList(query)
 * ```
 */
export function useOrderList(query: Ref<OrderQuery> | OrderQuery) {
  const resolvedQuery = computed(() => unref(query))

  return useQuery<{ list: OrderEntity[], total: number }>({
    queryKey: ['mes:order', 'list', resolvedQuery],
    queryFn: () => orderApi.getPage(resolvedQuery.value),
    placeholderData: keepPreviousData,
  })
}

/**
 * # MES 生产订单详情查询 Hook
 *
 * @example
 * ```ts
 * const { data, isLoading } = useOrderDetail(1)
 * ```
 */
export function useOrderDetail(id: Ref<number | undefined> | number | undefined) {
  return useQuery<OrderEntity>({
    queryKey: ['mes:order', 'detail', id],
    queryFn: () => orderApi.getDetail(unref(id)!),
    enabled: () => unref(id) !== undefined && unref(id) !== null,
  })
}

/**
 * # MES 生产订单审核 Mutation
 */
export function useOrderAudit() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: BillAuditRequest) => orderApi.audit(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['mes:order'] })
    },
  })
}

/**
 * # MES 生产订单驳回 Mutation
 */
export function useOrderReject() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: BillRejectRequest) => orderApi.reject(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['mes:order'] })
    },
  })
}

/**
 * # MES 生产订单完成明细 Mutation
 */
export function useOrderFinish() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: BillFinishRequest) => orderApi.setBillDetailsAllFinished(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['mes:order'] })
    },
  })
}

/**
 * # MES 生产订单开始生产 Mutation
 */
export function useOrderStart() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: { id: number }) => orderApi.start(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['mes:order'] })
    },
  })
}

/**
 * # MES 生产订单暂停生产 Mutation
 */
export function useOrderPause() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: { id: number }) => orderApi.pause(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['mes:order'] })
    },
  })
}

/**
 * # MES 生产订单报工 Mutation（addOrderDetail）
 */
export function useOrderReport() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: OrderReportRequest) => orderApi.addOrderDetail(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['mes:order'] })
    },
  })
}
