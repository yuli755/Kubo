import type { Ref } from 'vue'
import type { ContractEntity, ContractQuery } from '@/api/asset/types'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, unref } from 'vue'
import { contractApi } from '@/api/asset'

// F3 数据层收敛（D5）：视图层禁止直接 import `@/model/**Service`。
// 合同的 CRUD 走 airpower 的 Service 类，由本 hook 模块统一转发。
export { ContractService } from '@/model/asset/contract/ContractService'

export function useContractList(query: Ref<ContractQuery> | ContractQuery) {
  const resolvedQuery = computed(() => unref(query))
  return useQuery<{ list: ContractEntity[], total: number }>({
    queryKey: ['asset:contract', 'list', resolvedQuery],
    queryFn: () => contractApi.getPage(resolvedQuery.value),
    placeholderData: keepPreviousData,
  })
}

export function useContractDetail(id: Ref<number | undefined> | number | undefined) {
  return useQuery<ContractEntity>({
    queryKey: ['asset:contract', 'detail', id],
    queryFn: () => contractApi.getDetail(unref(id)!),
    enabled: () => unref(id) !== undefined && unref(id) !== null,
  })
}

export function useContractDelete() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => contractApi.delete(id),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['asset:contract'] }) },
  })
}

export function useContractEnforce() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: { id: number }) => contractApi.enforce(data),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['asset:contract'] }) },
  })
}

export function useContractStop() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: { id: number }) => contractApi.stop(data),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['asset:contract'] }) },
  })
}
