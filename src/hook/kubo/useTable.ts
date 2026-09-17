import type { Ref } from 'vue'
import type { AbstractCurdService, IIdentifiableEntity, QueryResponsePage } from '@/framework'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, ref } from 'vue'
import {
  FeedbackUtil,
  QueryRequestPage as FrameworkQueryRequestPage,
  QueryRequest,
  QuerySort,
} from '@/framework'

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
  /**
   * 树形数据（`getTreeList`）：仓库/生产单元/部门/菜单这类实体后端返回的是**嵌套树**，
   * 不分页、不带 `page` 参数。置 `true` 后 `KTable` 会自动开树渲染（`row-key` + 子节点）。
   */
  tree?: boolean
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
  /** 排序对象（分页与树查询共用） */
  function buildSort(): QuerySort | undefined {
    if (!sort.value) {
      return undefined
    }
    return new QuerySort()
      .setField(sort.value.field)
      .setDirection(sort.value.desc ? 'DESC' : 'ASC')
  }

  const request = computed(() => {
    const payload = new FrameworkQueryRequestPage<E>(EntityClass as never)
    payload.page.pageNum = page.value
    payload.page.pageSize = pageSize.value
    if (Object.keys(filter.value).length) {
      payload.filter = filter.value as E
    }
    const nextSort = buildSort()
    if (nextSort) {
      payload.sort = nextSort
    }
    return payload
  })

  /** 树查询请求体：`{ filter, sort }`，**没有** `page`（后端 `getTreeList` 不接受分页） */
  const treeRequest = computed(() => {
    const payload = new QueryRequest<E>(EntityClass as never)
    if (Object.keys(filter.value).length) {
      payload.filter = filter.value as E
    }
    const nextSort = buildSort()
    if (nextSort) {
      payload.sort = nextSort
    }
    return payload
  })

  const queryKey = computed(() => [
    ...option.queryKey,
    { page: page.value, pageSize: pageSize.value, sort: sort.value, filter: filter.value },
  ])

  /**
   * 分页接口返回 `QueryResponsePage`、树接口返回 `E[]`，两种形状都要能装进同一个 query。
   * 显式标注泛型：不写的话 TanStack 会按「两个重载二选一」推断，报
   * `Promise<QueryResponsePage<E>> is not assignable to Promise<E[]>`。
   */
  const query = useQuery<E[] | QueryResponsePage<E>>({
    queryKey,
    queryFn: async (): Promise<E[] | QueryResponsePage<E>> =>
      (option.tree ? service.getTreeList(treeRequest.value) : service.getPage(request.value)),
    // 翻页/改排序时保留上一页数据，避免表格整块闪空（这是 WMS 列表的日常操作）
    placeholderData: keepPreviousData,
  })

  /**
   * 列表数据。
   * 分页接口返回 `QueryResponsePage`，树接口返回 `E[]` —— 两者都在这里归一成数组，
   * 组件层不需要知道走的是哪个接口。
   */
  const list = computed<E[]>(() => {
    const data = query.data.value
    if (!data) {
      return []
    }
    return Array.isArray(data) ? data : data.list
  })
  /** 总数：树数据没有服务端总数，用展开后的节点数兜底（分页器在树模式下不渲染） */
  const total = computed<number>(() => {
    const data = query.data.value
    if (!data) {
      return 0
    }
    if (Array.isArray(data)) {
      const count = (nodes: E[]): number => nodes.reduce((sum, node) => {
        const children = (node as unknown as { children?: E[] }).children
        return sum + 1 + (children?.length ? count(children) : 0)
      }, 0)
      return count(data)
    }
    return data.total ?? 0
  })
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
    /** 是否树模式（KTable 据此渲染树 + 隐藏分页器 + 显示「添加子级」） */
    isTree: computed(() => !!option.tree),
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
