import type { Ref } from 'vue'
import type { StorageEntity } from '@/api/factory/types'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { unref } from 'vue'
import { storageApi } from '@/api/factory'

// 新底座服务（F2 起的页面用）：同样是"视图只从 hook 取"的约定，避免视图层直接依赖 @/model
export { KStorageService } from '@/model/factory/storage/KStorageService'
// F3 数据层收敛（D5）：视图层禁止直接 import `@/model/**Service`。
// 仓库的 CRUD 走 airpower 的 Service 类，由本 hook 模块统一转发（wms/inventory 等跨模块视图也从这里取）。
export { StorageService } from '@/model/factory/storage/StorageService'

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
