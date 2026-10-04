import type { Ref } from 'vue'
import type { AbstractCurdService, IIdentifiableEntity, ITransformerConstructor } from '@/framework'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, unref } from 'vue'

/**
 * # 自研 `useDetail` —— 只读详情的数据流（替代 airpower `useDetail`）
 *
 * 原库的 `useDetail(props, ServiceClass)` 把「弹窗参数里的实体」当数据源 —— 数据是从列表
 * 带过来的，不是重新拉的。这在单据场景会**看到过期数据**（列表是 5 分钟前拉的，
 * 中间别人改了审核状态，详情还是旧的）。这里统一按 id 重新查询，并交给 TanStack 缓存。
 *
 * ## 用法
 * ```ts
 * const detail = useDetail(InputService, { queryKey: ['wms:input'], id: props.id })
 * detail.entity.value?.status
 * ```
 */
interface IUseDetailOption {
  /** TanStack Query 缓存键（同域共享，如 `['wms:input']`） */
  queryKey: string[]
  /** 记录 id（可传 ref，弹窗里通常是 props.id） */
  id: Ref<number | null | undefined> | number | null | undefined
  /** 是否启用（默认：有 id 才查） */
  enabled?: Ref<boolean> | boolean
}

export function useDetail<E extends IIdentifiableEntity, S extends AbstractCurdService<E>>(
  // `entityClass` 出现在参数类型里只为类型推断：E 只出现在 S 的约束里时 TS 会推不出来（同 useTable）
  ServiceClass: new () => S & { entityClass: ITransformerConstructor<E> },
  option: IUseDetailOption,
) {
  const service = new ServiceClass()
  const currentId = computed(() => unref(option.id))
  const enabled = computed(() => (option.enabled === undefined ? true : unref(option.enabled)))
  const queryClient = useQueryClient()

  const query = useQuery({
    queryKey: computed(() => [...option.queryKey, 'detail', currentId.value]),
    queryFn: () => service.getDetail(currentId.value as number),
    enabled: computed(() => enabled.value && currentId.value !== null && currentId.value !== undefined),
  })

  /**
   * 单据状态变了以后刷新自己**和同域的一切**（列表、别的弹窗里的同一份数据）。
   *
   * 只 `query.refetch()` 是自己的：真机踩到过 —— 出库单「作业」完成后详情自己新了，
   * 列表那行还停在「出库中」，得整页重载才对得上（`useTable.reload()` 走的是同一前缀
   * 失效，审核能用、作业不能用，差别就在这）。这里改成失效 `option.queryKey` 整个前缀：
   * 自己（`[...queryKey, 'detail', id]`）与列表都在前缀里，挂载着的会立刻重新取数，
   * 没挂载的被标脏，下次进入自动新 —— 与 `useTable.invalidate()` 同一把尺子。
   *
   * `extra` 给后端的跨域联动留口子：出库完成会把销售单置「已完成」、把库存扣减，
   * 这些属别的域（`channel:sale` / `wms:inventory`），调用方按事实多传几个键即可，
   * 不必让详情页去 import 别人的列表。
   */
  async function reload(extra?: string[][]): Promise<void> {
    const keys = [option.queryKey, ...(extra ?? [])]
    await Promise.all(keys.map(key => queryClient.invalidateQueries({ queryKey: key })))
  }

  return {
    /** 详情实体（未加载完为 undefined） */
    entity: computed(() => query.data.value as E | undefined),
    isLoading: computed(() => query.isFetching.value),
    error: query.error,
    reload,
    service,
    entityClass: service.entityClass,
  }
}
