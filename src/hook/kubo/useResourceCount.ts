import type { Ref } from 'vue'
import type { AbstractCurdService, IIdentifiableEntity, QueryRequest } from '@/framework'
import { keepPreviousData, useQuery } from '@tanstack/vue-query'
import { computed, unref } from 'vue'
import { QueryRequest as FrameworkQueryRequest } from '@/framework'

/**
 * # 统计条数据流（后端 `getCount`）
 *
 * ## 为什么有这个东西
 * 后端的四个 WMS 读出口（入库 / 出库 / 移库 / 库存）都实现了 `getCount`（**只返回行数**，
 * 不做价格/金额聚合 —— 见 `base/mapper/ReadStat` 注释里的理由：聚合结果会成为绕过列级脱敏的旁路），
 * 但 2026-09-19 之前前端**一个消费者都没有**：端点在契约里活着，页面上看不见。
 * 列表页最需要的恰恰是它 —— 「审核中几单、待作业几单」是仓管每天开工先看的数。
 *
 * ## 与 `useTable` 的关系
 * - **共享同一个筛选**：`filter` 直接接 `table.filter`，用户在搜索栏改条件，统计条跟着变；
 * - **共享同一个 queryKey 前缀**：失效（`table.reload()`）时统计条一起刷新，不会出现
 *   「表格 3 条、统计条还是 8」这种自相矛盾的画面。
 *
 * ## 降级
 * 单项请求失败（例如当前角色没有该资源的 `getCount` 权限码，后端返回 403）时该项计**空值**，
 * 页面显示 `-` —— 统计条是辅助信息，不该因为它把整页搞成错误态。
 */

/** 一个统计项：`key` 传则统计条可点（点击回调把 `[field]: key` 交给页面去筛表） */
interface ICountItem {
  /** 该项使用的筛选字段名，如 `status` */
  field: string
  /** 筛选值；不传 = 只统计总数（不可点击） */
  key?: number | string
  /** 展示文案 */
  label: string
  /** 语义色（一般取枚举项自带的 `color`） */
  color?: string
  /** 忽略当前筛选：只统计全量（如库存页的「总记录」基线；默认 false） */
  ignoreFilter?: boolean
}

interface ICountCell extends ICountItem {
  /** 计数；`null` = 该项查询失败/无权限（显示 `-`） */
  value: number | null
  /** 是否当前生效的筛选（页面据此高亮） */
  active: boolean
}

interface IUseResourceCountOption {
  /** TanStack Query 缓存键（与 `useTable` 同前缀，便于一起失效） */
  queryKey: string[]
  /** 统计项（按状态枚举给；每次取最新值，因为它通常由字典派生） */
  items: () => ICountItem[]
  /** 当前表格筛选（一般直接传 `table.filter`） */
  filter?: Ref<Record<string, unknown>> | (() => Record<string, unknown>)
  /** 是否启用（默认启用） */
  enabled?: Ref<boolean> | boolean
}

export function useResourceCount<E extends IIdentifiableEntity, S extends AbstractCurdService<E>>(
  service: S,
  option: IUseResourceCountOption,
) {
  const filter = computed<Record<string, unknown>>(() => {
    const source = option.filter
    return source ? { ...unref(source as Ref<Record<string, unknown>>) } : {}
  })
  const items = computed(() => option.items())
  const enabled = computed(() => (option.enabled === undefined ? true : unref(option.enabled)))

  const query = useQuery({
    queryKey: computed(() => [
      ...option.queryKey,
      'counts',
      items.value.map(item => `${item.field}:${item.key ?? '*'}`),
      filter.value,
    ]),
    queryFn: async (): Promise<Array<number | null>> => Promise.all(items.value.map(async (item) => {
      const request = new FrameworkQueryRequest(service.entityClass as never) as QueryRequest<E>
      request.filter = {
        ...(item.ignoreFilter ? {} : filter.value),
        ...(item.key === undefined ? {} : { [item.field]: item.key }),
      } as unknown as E
      try {
        return await service.getCount(request)
      }
      catch {
        // 单项失败不拖垮整条：页面显示 `-`（常见原因是该角色没有 `{Entity}_getCount` 权限码）
        return null
      }
    })),
    enabled,
    // 换筛选条件时保留上一次的数字，避免统计条整条闪空
    placeholderData: keepPreviousData,
  })

  /** 统计条渲染用的单元格 */
  const cells = computed<ICountCell[]>(() => {
    const values = query.data.value ?? []
    return items.value.map((item, index) => ({
      ...item,
      value: values[index] ?? null,
      active: item.key !== undefined && filter.value[item.field] === item.key,
    }))
  })

  return {
    cells,
    isLoading: computed(() => query.isFetching.value),
  }
}
