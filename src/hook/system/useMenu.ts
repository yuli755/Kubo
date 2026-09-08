import type { Ref } from 'vue'
import type { MenuEntity, MenuQuery } from '@/api/system/types'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, unref } from 'vue'
import { menuApi } from '@/api/system'

// F3 数据层收敛（D5）：CRUD 走 airpower 的 Service 类，由本 hook 模块统一转发。
export { MenuService } from '@/model/system/menu/MenuService'

/**
 * # System 菜单列表查询 Hook
 */
export function useMenuList(query: Ref<MenuQuery> | MenuQuery) {
  const resolvedQuery = computed(() => unref(query))
  return useQuery<{ list: MenuEntity[], total: number }>({
    queryKey: ['system:menu', 'list', resolvedQuery],
    queryFn: () => menuApi.getPage(resolvedQuery.value),
    placeholderData: keepPreviousData,
  })
}

/**
 * # System 菜单树查询 Hook
 */
export function useMenuTree() {
  return useQuery<MenuEntity[]>({
    queryKey: ['system:menu', 'tree'],
    queryFn: () => menuApi.getTree(),
  })
}

/**
 * # System 菜单详情查询 Hook
 */
export function useMenuDetail(id: Ref<number | undefined> | number | undefined) {
  return useQuery<MenuEntity>({
    queryKey: ['system:menu', 'detail', id],
    queryFn: () => menuApi.getDetail(unref(id)!),
    enabled: () => unref(id) !== undefined && unref(id) !== null,
  })
}

/**
 * # System 菜单删除 Mutation Hook
 */
export function useMenuDelete() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => menuApi.delete(id),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['system:menu'] }) },
  })
}
