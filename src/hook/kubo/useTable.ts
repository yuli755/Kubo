import type { Ref } from 'vue'
import type { AbstractCurdService, IIdentifiableEntity } from '@/framework'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, ref } from 'vue'
import { FeedbackUtil, QueryRequestPage as FrameworkQueryRequestPage, QuerySort } from '@/framework'

/**
 * # 自研 `useTable` —— 表格页的状态与数据流（替代 airpower `useTable`）
 *
 * ## 与 airpower 版本的差别（不是换了个壳，而是少了三处隐性行为）
 *
 * | 点 | airpower `useTable` | 这里 |
 * | --- | --- | --- |
 * | 数据源 | `AbstractService` + 自建 `request/response` 双份 ref，视图直接读写 | TanStack Query（缓存/去重/占位数据），视图不碰 `request` 对象 |
 * | 分页/排序 | 由 `QueryRequestPage` 承担，`ATable` 直接改它的字段 | 收敛成本 hook 的 `page/pageSize/sort`，只暴露语义化方法 |
 * | 弹窗 | `DialogUtil` + `editView` 组件 + `onCallback` 回调 | 同样保留（这是业务真的需要的），但**提交/删除由本 hook 统一走 service**，页面不再自己写 CRUD |
 *
 * ## 为什么不用 airpower 的 `QueryRequestPage`
 * `@Table` 的列配置、`@Form` 的表单配置都由本仓 `src/framework` 的装饰器写入 WeakMap，
 * 与 airpower 的注册表是**两套**：混用会出现"列头有、数据没有"这类只在运行时看得见的现象。
 * F2 的目标就是让页面只依赖 `src/framework`，所以这里全部走框架层。
 *
 * ## 用法
 * ```ts
 * const table = useTable(UnitService, { queryKey: ['system:unit'], EditView: UnitEditor })
 * ```
 */

export interface IUseTableOption<E extends IIdentifiableEntity, S extends AbstractCurdService<E>> {
  /** TanStack Query 缓存键（同域共享，如 `['system:unit']`） */
  queryKey: string[]
  /** 编辑弹窗组件（通常由 `defineAsyncComponent` 或直接 import 得到） */
  EditView?: unknown
  /** 详情弹窗组件（不传则复用 EditView） */
  DetailView?: unknown
  /** 默认每页条数 */
  defaultPageSize?: number
  /** 默认过滤条件（如按父级 id 过滤） */
  defaultFilter?: Partial<E>
  /** 默认排序字段 */
  defaultSortField?: string
  /** 默认排序方向 */
  defaultSortDesc?: boolean
  /** 服务实例（不传则 new 一个；用于注入带自定义配置的服务） */
  service?: S
}

export interface ITableSort {
  field: string
  desc: boolean
}

export function useTable<E extends IIdentifiableEntity, S extends AbstractCurdService<E>>(
  ServiceClass: new () => S,
  option: IUseTableOption<E, S> = { queryKey: [] },
) {
  const service = option.service ?? new ServiceClass()
  const EntityClass = service.entityClass
  const queryClient = useQueryClient()

  const page = ref(1)
  const pageSize = ref(option.defaultPageSize ?? 20)
  const sort = ref<ITableSort | null>(
    option.defaultSortField
      ? { field: option.defaultSortField, desc: option.defaultSortDesc ?? true }
      : null,
  )
  const filter = ref<Record<string, unknown>>({ ...(option.defaultFilter ?? {}) })

  /**
   * 请求体：后端契约是 `{ filter, page: { pageNum, pageSize }, sort: { field, direction } }`
   * （见 `framework/query.ts` 与 `docs` 里的接口约定；`getPage` 会把响应 fill 成实体实例）。
   */
  const request = computed(() => {
    const payload = new FrameworkQueryRequestPage<E>(EntityClass as never)
    payload.page.pageNum = page.value
    payload.page.pageSize = pageSize.value
    if (Object.keys(filter.value).length) {
      payload.filter = filter.value as E
    }
    if (sort.value) {
      payload.sort = new QuerySort()
        .setField(sort.value.field)
        .setDirection(sort.value.desc ? 'DESC' : 'ASC')
    }
    return payload
  })

  const queryKey = computed(() => [
    ...option.queryKey,
    { page: page.value, pageSize: pageSize.value, sort: sort.value, filter: filter.value },
  ])

  const query = useQuery({
    queryKey,
    queryFn: () => service.getPage(request.value),
    // 翻页/改排序时保留上一页数据，避免表格整块闪空（这是 WMS 列表的日常操作）
    placeholderData: keepPreviousData,
  })

  const list = computed<E[]>(() => (query.data.value?.list ?? []) as E[])
  const total = computed<number>(() => query.data.value?.total ?? 0)
  const isLoading = computed(() => query.isFetching.value)

  function invalidate() {
    queryClient.invalidateQueries({ queryKey: option.queryKey })
  }

  /** 搜索：条件变化后必须回到第 1 页，否则会停在一个可能不存在的页码上 */
  function search(newFilter: Record<string, unknown> = filter.value) {
    filter.value = { ...newFilter }
    page.value = 1
  }

  function reset() {
    filter.value = { ...(option.defaultFilter ?? {}) }
    page.value = 1
    sort.value = option.defaultSortField
      ? { field: option.defaultSortField, desc: option.defaultSortDesc ?? true }
      : null
  }

  function onPageChanged(nextPage: number, nextPageSize = pageSize.value) {
    page.value = nextPage
    pageSize.value = nextPageSize
  }

  function onSortChanged(next: ITableSort | null) {
    sort.value = next
    page.value = 1
  }

  // ── 写操作：统一在这里做「调用 → 提示 → 失效缓存」，页面不再各写一份 ──
  const removeMutation = useMutation({
    mutationFn: (id: number) => service.delete(id),
    onSuccess: () => {
      FeedbackUtil.toastSuccess('删除成功')
      invalidate()
    },
  })

  const toggleMutation = useMutation({
    mutationFn: ({ id, enable }: { id: number, enable: boolean }) =>
      enable ? service.enable(id) : service.disable(id),
    onSuccess: (_data, variables) => {
      FeedbackUtil.toastSuccess(variables.enable ? '启用成功' : '禁用成功')
      invalidate()
    },
  })

  /** 删除（带二次确认：这是不可逆操作，确认框不能省） */
  async function onDelete(id: number, label = '该记录') {
    await FeedbackUtil.confirmWarning(`确定删除${label}吗？删除后不可恢复。`)
    await removeMutation.mutateAsync(id)
  }

  async function onToggleEnable(id: number, enable: boolean) {
    await toggleMutation.mutateAsync({ id, enable })
  }

  return {
    // 数据
    list,
    total,
    isLoading,
    error: query.error,
    // 查询条件
    page,
    pageSize,
    sort,
    filter,
    request,
    // 方法
    search,
    reset,
    onPageChanged,
    onSortChanged,
    onDelete,
    onToggleEnable,
    reload: invalidate,
    // 组件与元数据（供 KTable / 弹窗使用）
    service,
    entityClass: EntityClass,
    EditView: option.EditView,
    DetailView: option.DetailView ?? option.EditView,
    queryKey: option.queryKey,
  }
}

export type UseTableReturn<E extends IIdentifiableEntity, S extends AbstractCurdService<E>> = ReturnType<typeof useTable<E, S>>

/** 供页面在 `sort` 变化时读取（类型收敛，避免各处重复断言） */
export type TableSortRef = Ref<ITableSort | null>
