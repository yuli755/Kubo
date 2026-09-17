import type { Ref } from 'vue'
import type { AbstractCurdService, IIdentifiableEntity } from '@/framework'
import { useQuery } from '@tanstack/vue-query'
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
  ServiceClass: new () => S,
  option: IUseDetailOption,
) {
  const service = new ServiceClass()
  const currentId = computed(() => unref(option.id))
  const enabled = computed(() => (option.enabled === undefined ? true : unref(option.enabled)))

  const query = useQuery({
    queryKey: computed(() => [...option.queryKey, 'detail', currentId.value]),
    queryFn: () => service.getDetail(currentId.value as number),
    enabled: computed(() => enabled.value && currentId.value !== null && currentId.value !== undefined),
  })

  return {
    /** 详情实体（未加载完为 undefined） */
    entity: computed(() => query.data.value as E | undefined),
    isLoading: computed(() => query.isFetching.value),
    error: query.error,
    reload: () => query.refetch(),
    service,
    entityClass: service.entityClass,
  }
}
