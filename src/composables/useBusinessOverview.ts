import type { DashboardOverviewItem } from '@/api/system/types'
import { onScopeDispose, shallowReactive } from 'vue'
import { dashboardApi } from '@/api/system'

/**
 * # 业务概况（P2 · 数据权限感知看板）
 *
 * ## 它回答的问题
 * 「我」在当前租户与行级权限下，各资源有多少行。数据来源是 `dashboard/overview` ——
 * 后端对每个资源走 `getMapperCount`（Mapper 权限拦截链）再聚合，前端只搬运。
 *
 * ## 为什么这里没有任何判定
 * 每一档 count 已经是被权限过滤后的结果；前端若按「哪些资源该上概览 / 怎么算总数」再实现一遍，
 * 就是第二份真相，资源清单一改就会开始说谎。与安全态势看板同一条纪律：只回显、不重算。
 *
 * ## 失败取向
 * 失败只置 `failed=true`，不清空已有结果、也不渲染成「全部为 0」：刷新失败不该把「已知有单据」
 * 擦成「看起来没数据」。空数组（后端正常返回但无资源）与「接口失败」由此可区分。
 */
export interface BusinessOverviewState {
  loading: boolean
  failed: boolean
  ready: boolean
  items: DashboardOverviewItem[]
  refresh: () => Promise<void>
}

export function useBusinessOverview(): BusinessOverviewState {
  const state = shallowReactive<BusinessOverviewState>({
    loading: false,
    failed: false,
    ready: false,
    items: [],
    refresh: async () => undefined,
  })

  const refresh = async (): Promise<void> => {
    state.loading = true
    try {
      const data = await dashboardApi.getOverview()
      state.items = Array.isArray(data) ? data : []
      state.failed = false
      state.ready = true
    }
    catch {
      state.failed = true
    }
    finally {
      state.loading = false
    }
  }

  state.refresh = refresh
  void refresh()
  onScopeDispose(() => {
    // 目前没有订阅要清理；留这一行提醒后来者：这里若加轮询，必须随作用域释放
  })

  return state
}
