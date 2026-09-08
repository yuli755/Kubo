import type { Ref } from 'vue'
import type { StructureEntity } from '@/api/factory/types'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { unref } from 'vue'
import { structureApi } from '@/api/factory'

// F3 数据层收敛（D5）：视图层禁止直接 import `@/model/**Service`。
// 生产单元的 CRUD 走 airpower 的 Service 类，由本 hook 模块统一转发（wms/inventory 等跨模块视图也从这里取）。
export { StructureService } from '@/model/factory/structure/StructureService'

/**
 * # Factory 生产单元树数据查询 Hook
 *
 * 生产单元为树形实体（ITree），使用 `getList` 获取全量树数据，无需分页。
 *
 * @example
 * ```ts
 * const { data, isFetching } = useStructureTree()
 * ```
 */
export function useStructureTree() {
  return useQuery<StructureEntity[]>({
    queryKey: ['factory:structure', 'tree'],
    queryFn: () => structureApi.getList(),
  })
}

/**
 * # Factory 生产单元详情查询 Hook
 *
 * @example
 * ```ts
 * const { data, isLoading } = useStructureDetail(1)
 * ```
 */
export function useStructureDetail(id: Ref<number | undefined> | number | undefined) {
  return useQuery<StructureEntity>({
    queryKey: ['factory:structure', 'detail', id],
    queryFn: () => structureApi.getDetail(unref(id)!),
    enabled: () => unref(id) !== undefined && unref(id) !== null,
  })
}

/**
 * # Factory 生产单元发布 Mutation
 *
 * 发布成功后自动失效树数据缓存。
 */
export function useStructurePublish() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: { id: number }) => structureApi.publish(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['factory:structure'] })
    },
  })
}
