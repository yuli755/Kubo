import type { Ref } from 'vue'
import type { ConfigEntity, ConfigQuery } from '@/api/system/types'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, unref } from 'vue'
import { configApi } from '@/api/system'

/**
 * # System 参数配置列表查询 Hook
 */
export function useConfigList(query: Ref<ConfigQuery> | ConfigQuery) {
  const resolvedQuery = computed(() => unref(query))
  return useQuery<{ list: ConfigEntity[], total: number }>({
    queryKey: ['system:config', 'list', resolvedQuery],
    queryFn: () => configApi.getPage(resolvedQuery.value),
    placeholderData: keepPreviousData,
  })
}

/**
 * # System 参数配置详情查询 Hook
 */
export function useConfigDetail(id: Ref<number | undefined> | number | undefined) {
  return useQuery<ConfigEntity>({
    queryKey: ['system:config', 'detail', id],
    queryFn: () => configApi.getDetail(unref(id)!),
    enabled: () => unref(id) !== undefined && unref(id) !== null,
  })
}

/**
 * # System 参数配置删除 Mutation Hook
 */
export function useConfigDelete() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => configApi.delete(id),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['system:config'] }) },
  })
}
