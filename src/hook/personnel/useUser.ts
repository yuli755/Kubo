import type { Ref } from 'vue'
import type { UserEntity, UserQuery } from '@/api/personnel/types'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, unref } from 'vue'
import { userApi } from '@/api/personnel'

// F3 数据层收敛（D5）：视图层禁止直接 import `@/model/**Service`。
// 用户的 CRUD 走 airpower 的 Service 类（useTanStackTable / useEditor / useSelector 需要类实例），
// 因此由本 hook 模块统一转发 —— 视图从这里取，不再回头依赖 @/model。
export { UserService } from '@/model/personnel/user/UserService'

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
