import type { Ref } from 'vue'
import type { PermissionEntity } from '@/api/types'
import { useQuery } from '@tanstack/vue-query'
import { unref } from 'vue'
import { request } from '@/api/axios'
import { permissionApi } from '@/api/system'

/**
 * # System 权限树数据查询 Hook
 *
 * 权限为树形实体（ITree），使用 `getTree` 获取全量树数据，无需分页。
 *
 * @example
 * ```ts
 * const { data, isFetching } = usePermissionTree()
 * ```
 */
export function usePermissionTree() {
  return useQuery<PermissionEntity[]>({
    queryKey: ['system:permission', 'tree'],
    queryFn: () => permissionApi.getTree(),
  })
}

/**
 * # System 权限详情查询 Hook
 *
 * @example
 * ```ts
 * const { data, isLoading } = usePermissionDetail(1)
 * ```
 */
export function usePermissionDetail(id: Ref<number | undefined> | number | undefined) {
  return useQuery<PermissionEntity>({
    queryKey: ['system:permission', 'detail', id],
    queryFn: () => permissionApi.getDetail(unref(id)!),
    enabled: () => unref(id) !== undefined && unref(id) !== null,
  })
}

/**
 * # 当前用户权限标识列表查询 Hook
 *
 * 从后端获取当前用户的权限标识列表（string[]），用于权限检查。
 * 配合 `usePermissionStore` 使用，加载后自动写入 store。
 *
 * @example
 * ```ts
 * const { data, isFetching } = useUserPermissionList()
 * ```
 */
export function useUserPermissionList() {
  return useQuery<string[]>({
    queryKey: ['user:permission', 'list'],
    queryFn: () => request.post('/user/getMyPermissionList') as Promise<string[]>,
    staleTime: 5 * 60_000, // 5分钟内不重新请求
  })
}
