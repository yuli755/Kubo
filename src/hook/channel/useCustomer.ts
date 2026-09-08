import type { Ref } from 'vue'
import type { CustomerEntity, CustomerQuery } from '@/api/channel/types'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, unref } from 'vue'
import { customerApi } from '@/api/channel'

// F3 数据层收敛（D5）：视图层禁止直接 import `@/model/**Service`。
// 客户的 CRUD 走 airpower 的 Service 类，由本 hook 模块统一转发。
export { CustomerService } from '@/model/channel/customer/CustomerService'

export function useCustomerList(query: Ref<CustomerQuery> | CustomerQuery) {
  const resolvedQuery = computed(() => unref(query))
  return useQuery<{ list: CustomerEntity[], total: number }>({
    queryKey: ['channel:customer', 'list', resolvedQuery],
    queryFn: () => customerApi.getPage(resolvedQuery.value),
    placeholderData: keepPreviousData,
  })
}

export function useCustomerDetail(id: Ref<number | undefined> | number | undefined) {
  return useQuery<CustomerEntity>({
    queryKey: ['channel:customer', 'detail', id],
    queryFn: () => customerApi.getDetail(unref(id)!),
    enabled: () => unref(id) !== undefined && unref(id) !== null,
  })
}

export function useCustomerDelete() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => customerApi.delete(id),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['channel:customer'] }) },
  })
}
