import type { Ref } from 'vue'
import type { DepartmentEntity, DepartmentQuery } from '@/api/personnel/types'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, unref } from 'vue'
import { departmentApi } from '@/api/personnel'

export function useDepartmentList(query: Ref<DepartmentQuery> | DepartmentQuery) {
  const resolvedQuery = computed(() => unref(query))
  return useQuery<{ list: DepartmentEntity[], total: number }>({
    queryKey: ['personnel:department', 'list', resolvedQuery],
    queryFn: () => departmentApi.getPage(resolvedQuery.value),
    placeholderData: keepPreviousData,
  })
}

export function useDepartmentTree() {
  return useQuery<DepartmentEntity[]>({
    queryKey: ['personnel:department', 'tree'],
    queryFn: () => departmentApi.getTree(),
  })
}

export function useDepartmentDetail(id: Ref<number | undefined> | number | undefined) {
  return useQuery<DepartmentEntity>({
    queryKey: ['personnel:department', 'detail', id],
    queryFn: () => departmentApi.getDetail(unref(id)!),
    enabled: () => unref(id) !== undefined && unref(id) !== null,
  })
}

export function useDepartmentDelete() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => departmentApi.delete(id),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['personnel:department'] }) },
  })
}
