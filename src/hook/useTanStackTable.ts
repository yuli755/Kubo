import type { CurdServiceConstructor, ITableOption, ITableResult, QueryPage, QuerySort } from '@airpower/web'
import type { Ref } from 'vue'
import type { AbstractBaseService } from '@/base/AbstractBaseService'
import type { BaseEntity } from '@/base/BaseEntity'
import { DialogUtil, FeedbackUtil, QueryRequestPage, QueryResponsePage } from '@airpower/web'
import { useQuery } from '@tanstack/vue-query'
import { computed, ref, watch } from 'vue'
import { useUserStore } from '@/stores/user'

/**
 * # TanStack Query → airpower ATable 适配器
 *
 * 将 TanStack Query 的 `useQuery` 数据流适配为 `ITableResult` 接口，
 * 使 ATable 组件可直接使用 TanStack Query 进行数据查询（缓存、去重、stale-while-revalidate），
 * 同时保留 airpower 的 CRUD 对话框逻辑（add / edit / delete / detail / enable / disable）。
 *
 * 本质上是 airpower `useTable()` 的 TanStack 替代品 —— 查询走 TanStack，CRUD 复用 airpower Service。
 *
 * ## 使用示例
 * ```ts
 * const hook = useTanStackTable(InventoryService, {
 *   queryKey: ['wms:inventory'],
 *   editView: InventoryEditor,
 * })
 * ```
 *
 * @param serviceClass - airpower 实体 Service 类
 * @param option - ATable 配置项 + TanStack queryKey
 * @returns ITableResult 兼容对象
 */
export function useTanStackTable<E extends BaseEntity, S extends AbstractBaseService<E>>(
  serviceClass: CurdServiceConstructor<E, S>,
  option: ITableOption<E> & {
    /** TanStack Query 的 queryKey 前缀 */
    queryKey: string[]
  },
): ITableResult<E, S> {
  // ⚠️ 顺序不能反：airpower 的 Service 把 `entityClass` 声明为**实例字段**
  // （`entityClass = MaterialEntity`），类对象上是 undefined —— 先取 `serviceClass.entityClass`
  // 会得到 `new undefined()`，抛「serviceClass.entityClass is not a constructor」，
  // 整个列表页被 Console 的错卡接住。官方 `useEditor` 同样是先 new 实例、再从实例取字段。
  // 传入的是"类本身"（参数名小写是约定，不是实例），因此 new 首字母小写是刻意的
  // eslint-disable-next-line new-cap
  const service = new serviceClass() as S
  const EntityClass = service.entityClass
  // eslint-disable-next-line new-cap
  const entity: E = new EntityClass()
  const userStore = useUserStore()

  // ── 查询状态 ──
  // ⚠️ `ATable` 会直接读写 `request.value.filter`（搜索表单要 `Object.keys(filter)` 与 `filter.copy()`），
  // 而 `QueryRequestPage` 只有在收到**实体类构造器**时才会 `new FilterClass()` 初始化 filter；
  // 传实体实例会被它内部 try/catch 静默吞掉，filter 停在 undefined，ATable 一 setup 就抛
  // `Cannot convert undefined or null to object` → 整页变错卡。这里必须传类。
  // 断言成 Ref<QueryRequestPage<E>>：`ref()` 的深层解包会把 filter 变成 UnwrapRef<E>，
  // 与 `ITableResult.request` 的声明不符（airpower 自己也是这么拆开写的）
  const request = ref(new QueryRequestPage<E>(EntityClass)) as Ref<QueryRequestPage<E>>
  if (option.defaultFilter) {
    request.value.filter = option.defaultFilter
  }
  const response = ref<QueryResponsePage<E>>(new QueryResponsePage<E>())
  const selectList = ref<E[]>([])
  const isLoading = ref(false)
  // airpower 的 HTTP 层通过 `service.loading` 显示请求态，与 useBaseTable 保持一致
  service.loading = isLoading

  const resolvedRequest = computed(() => request.value)
  // 租户是查询身份的一部分；不把它放进 key 会让切换租户复用旧缓存。
  const tenantQueryKey = computed(() => [
    ...option.queryKey,
    'tenant',
    userStore.tenantId ?? 'unresolved',
    'list',
    resolvedRequest.value,
  ])

  const { data, isFetching, refetch } = useQuery<QueryResponsePage<E>>({
    queryKey: tenantQueryKey,
    queryFn: () => service.getPage(request.value as any),
  })

  watch(isFetching, (val) => {
    isLoading.value = val
  })
  watch(data, (val) => {
    if (val) {
      response.value = val
    }
  })
  watch(() => userStore.tenantId, (tenantId, previousTenantId) => {
    if (tenantId !== previousTenantId) {
      // 切换租户期间不得继续展示上一个租户的列表；新的 query key 会重新取真实数据。
      response.value = new (QueryResponsePage as any)()
    }
  })

  /** 提取 list 为 computed ref 以保持 ITableResult 兼容 */
  const list = computed(() => response.value.list) as unknown as Ref<E[]>

  // ── 数据操作方法 ──
  function onGetList() {
    refetch()
  }

  function onSearch(req: QueryRequestPage<E>) {
    request.value = req
  }

  function onPageChanged(page: QueryPage) {
    request.value.page = page
  }

  function onSortChanged(sort?: QuerySort) {
    if (sort) {
      request.value.setSort(sort)
    }
  }

  function onReloadData() {
    request.value.page.pageNum = 1
    refetch()
  }

  async function onAdd() {
    if (option.editView) {
      await DialogUtil.show(option.editView)
    }
    onReloadData()
  }

  async function onEdit(row: E) {
    if (option.editView) {
      await DialogUtil.show(option.editView, row)
    }
    onReloadData()
  }

  async function onDetail(row: E) {
    if (option.detailView) {
      await DialogUtil.show(option.detailView, row)
    }
  }

  async function onDelete(row: E) {
    await FeedbackUtil.confirmWarning(`是否确认删除选择的数据？`)
    await service.delete(row.id)
    onReloadData()
  }

  async function onDisable(row: E) {
    await FeedbackUtil.confirmWarning(`是否确认禁用选择的数据？`)
    await service.disable(row.id)
    onReloadData()
  }

  async function onEnable(row: E) {
    await FeedbackUtil.confirmWarning(`是否确认启用选择的数据？`)
    await service.enable(row.id)
    onReloadData()
  }

  function onSelected(list: E[]) {
    selectList.value = list
  }

  return {
    isLoading,
    response,
    request,
    list,
    selectList,
    entity,
    service,
    serviceClass,
    onReloadData,
    onSearch,
    onPageChanged,
    onSortChanged,
    onDetail,
    onAdd,
    onEdit,
    onDelete,
    onDisable,
    onEnable,
    onSelected,
    onGetList,
  } as ITableResult<E, S>
}
