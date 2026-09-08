import type { Ref } from 'vue'
import type { DepartmentEntity, DepartmentQuery } from '@/api/personnel/types'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, unref } from 'vue'
import { departmentApi } from '@/api/personnel'

// F3 数据层收敛（D5）：视图层禁止直接 import `@/model/**Service`。
// 部门的 CRUD 走 airpower 的 Service 类（useTableTree / useEditor / useSelector 需要类实例），
// 因此由本 hook 模块统一转发 —— 视图从这里取，不再回头依赖 @/model。
export { DepartmentService } from '@/model/personnel/department/DepartmentService'

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
