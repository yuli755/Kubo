import type { Ref } from 'vue'
import type { RoleEntity, RoleQuery } from '@/api/personnel/types'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, unref } from 'vue'
import { roleApi } from '@/api/personnel'

export function useRoleList(query: Ref<RoleQuery> | RoleQuery) {
  const resolvedQuery = computed(() => unref(query))
  return useQuery<{ list: RoleEntity[], total: number }>({
    queryKey: ['personnel:role', 'list', resolvedQuery],
    queryFn: () => roleApi.getPage(resolvedQuery.value),
    placeholderData: keepPreviousData,
  })
}

export function useRoleDetail(id: Ref<number | undefined> | number | undefined) {
  return useQuery<RoleEntity>({
    queryKey: ['personnel:role', 'detail', id],
    queryFn: () => roleApi.getDetail(unref(id)!),
    enabled: () => unref(id) !== undefined && unref(id) !== null,
  })
}

export function useRoleDelete() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => roleApi.delete(id),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['personnel:role'] }) },
  })
}

export function useRoleDisable() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => roleApi.disable(id),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['personnel:role'] }) },
  })
}

export function useRoleEnable() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => roleApi.enable(id),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['personnel:role'] }) },
  })
}

export function useRoleAuthorizeMenu() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: { id: number, menuList: { id: number }[] }) =>
      roleApi.authorizeMenu(data.id, data.menuList),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['personnel:role'] }) },
  })
}

export function useRoleAuthorizePermission() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: { id: number, permissionList: { id: number }[] }) =>
      roleApi.authorizePermission(data.id, data.permissionList),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['personnel:role'] }) },
  })
}
