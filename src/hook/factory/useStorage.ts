import type { Ref } from 'vue'
import type { StorageEntity } from '@/api/factory/types'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { unref } from 'vue'
import { storageApi } from '@/api/factory'

/**
 * # Factory 仓库树数据查询 Hook
 *
 * 仓库为树形实体（ITree），使用 `getList` 获取全量树数据，无需分页。
 *
 * @example
 * ```ts
 * const { data, isFetching } = useStorageTree()
 * ```
 */
export function useStorageTree() {
  return useQuery<StorageEntity[]>({
    queryKey: ['factory:storage', 'tree'],
    queryFn: () => storageApi.getList(),
  })
}

/**
 * # Factory 仓库详情查询 Hook
 *
 * @example
 * ```ts
 * const { data, isLoading } = useStorageDetail(1)
 * ```
 */
export function useStorageDetail(id: Ref<number | undefined> | number | undefined) {
  return useQuery<StorageEntity>({
    queryKey: ['factory:storage', 'detail', id],
    queryFn: () => storageApi.getDetail(unref(id)!),
    enabled: () => unref(id) !== undefined && unref(id) !== null,
  })
}

/**
 * # Factory 仓库发布 Mutation
 *
 * 发布成功后自动失效树数据缓存。
 */
export function useStoragePublish() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: { id: number }) => storageApi.publish(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['factory:storage'] })
    },
  })
}
