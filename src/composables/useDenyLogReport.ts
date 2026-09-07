import type { DenyLogItem } from '@/api/system/types'
import { onScopeDispose, shallowReactive } from 'vue'
import { dataResourceApi } from '@/api/system'

/**
 * # 拒绝事件盘点（`data_permission_deny_log`）
 *
 * ## 它回答的问题
 * 放开 enforce 之后第一个被问的是「挡了谁」。此前拒绝只有 Micrometer 计数 + WARN 日志，
 * 事后追认（谁在何时、因何、在哪个档位下被拒）无处可查。这个报表把最近 50 条拒绝事件
 * 摆到数据资源管理页上，与其余盘点（导出缺口 / 维度绑定 / 放开就绪）同一处可见。
 *
 * ## 为什么这里没有任何判定逻辑
 * 「算不算拒绝」由后端四个拒答出口统一决定，档位由 `effectiveMode` 给出 —— 前端只搬运与
 * 摘要（时间 / 资源 / 原因 / 档位拼接），不重算任何一条判据。语义是「拒绝事件」而非
 * 「策略命中」，所以列表里没有策略 ID，只有原因与明细。
 *
 * ## 失败取向：沉默，但把失败显式化
 * 后端在「表没建 / 取不到」时返回空数组（不是报错），因此前端**分不清「空」是「没有记录」
 * 还是「取不到表」**：这里把「刷新抛错」标记为 `failed`，调用方显示「未能取得拒绝事件」；
 * 但仍存在「表存在但没记录」与「表不存在」同为空的歧义 —— 这一歧义由后端语义决定
 * （ObjectProvider 取不到 repository 时返回空），前端不再加绕过开关，只照实显示。
 */

export interface DenyLogState {
  loading: boolean
  failed: boolean
  ready: boolean
  items: DenyLogItem[]
  /** 最近条数（后端最多给 50），页面据此决定要不要显示整块 */
  count: number
  /** 前几条的人话摘要（「时间 · 资源 · 原因（档位）」），只是拼接，不重算判定 */
  preview: string[]
  refresh: () => Promise<void>
}

function timeOf(ms: number): string {
  if (!ms) {
    return '未知时间'
  }
  return new Date(ms).toLocaleString()
}

function previewOf(items: DenyLogItem[]): string[] {
  return items.slice(0, 3).map(item => `${timeOf(item.createTime)} · ${item.resourceKey} · ${item.reason}（${item.mode}）`)
}

export function useDenyLogReport(): DenyLogState {
  const state = shallowReactive<DenyLogState>({
    loading: false,
    failed: false,
    ready: false,
    items: [],
    count: 0,
    preview: [],
    refresh: async () => undefined,
  })

  const refresh = async (): Promise<void> => {
    state.loading = true
    try {
      const data = await dataResourceApi.getDenyLogs()
      state.items = Array.isArray(data) ? data : []
      state.count = state.items.length
      state.preview = previewOf(state.items)
      state.failed = false
      state.ready = true
    }
    catch {
      // 只标记失败，不清空上次结果：刷新失败不该把「已知有拒绝」擦成「看起来没人被拒」
      state.failed = true
    }
    finally {
      state.loading = false
    }
  }

  state.refresh = refresh
  void refresh()
  onScopeDispose(() => {
    // 目前没有订阅要清理；留这一行提醒后来者：这里若加轮询/重试，必须随作用域释放
  })

  return state
}
