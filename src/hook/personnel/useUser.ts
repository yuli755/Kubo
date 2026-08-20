import type { Ref } from 'vue'
import type { UserEntity, UserQuery } from '@/api/personnel/types'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, unref } from 'vue'
import { userApi } from '@/api/personnel'

export function useUserList(query: Ref<UserQuery> | UserQuery) {
  const resolvedQuery = computed(() => unref(query))
  return useQuery<{ list: UserEntity[], total: number }>({
    queryKey: ['personnel:user', 'list', resolvedQuery],
    queryFn: () => userApi.getPage(resolvedQuery.value),
    placeholderData: keepPreviousData,
  })
}

export function useUserDetail(id: Ref<number | undefined> | number | undefined) {
  return useQuery<UserEntity>({
    queryKey: ['personnel:user', 'detail', id],
    queryFn: () => userApi.getDetail(unref(id)!),
    enabled: () => unref(id) !== undefined && unref(id) !== null,
  })
}

export function useUserDelete() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => userApi.delete(id),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['personnel:user'] }) },
  })
}

export function useUserDisable() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => userApi.disable(id),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['personnel:user'] }) },
  })
}

export function useUserEnable() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => userApi.enable(id),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['personnel:user'] }) },
  })
}