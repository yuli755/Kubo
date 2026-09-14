import type { OperationLogItem } from '@/api/system/types'
import { onScopeDispose, shallowReactive } from 'vue'
import { dataResourceApi } from '@/api/system'

/**
 * # 操作审计盘点（`operation_log`）
 *
 * ## 它回答的问题
 * 「谁在何时把哪条记录改成了什么」—— 与拒绝事件盘点（`useDenyLogReport`）互补：
 * 拒绝事件回答「能不能看/改」，这里回答「改没改、改了啥」。触发点是 `BaseService` 的
 * 增/改/删钩子，只对标记 `@OperationLog` 的实体生效，身份在业务线程取成值后异步落库。
 *
 * ## 为什么这里没有任何判定逻辑
 * 「算不算一次操作」由后端 `BaseService` 钩子统一决定，动作只有 CREATE / UPDATE / DELETE，
 * 快照由后端 `OperationLogSnapshot` 只取标量字段 —— 前端只搬运与摘要，不重算任何一条判据。
 *
 * ## 失败取向：沉默，但把失败显式化
 * 后端在「表没建 / 取不到」时返回空数组（不是报错），因此前端**分不清「空」是「没有记录」
 * 还是「取不到表」**：这里把「刷新抛错」标记为 `failed`，调用方显示「未能取得操作审计」；
 * 但仍存在「表存在但没记录」与「表不存在」同为空的歧义 —— 由后端语义决定（ObjectProvider
 * 取不到 repository 时返回空），前端不再加绕过开关，只照实显示。
 */

export interface OperationLogState {
  loading: boolean
  failed: boolean
  ready: boolean
  items: OperationLogItem[]
  /** 最近条数（后端最多给 200），页面据此决定要不要显示整块 */
  count: number
  /** 前几条的人话摘要（「时间 · 资源 · 动作 · 目标」），只是拼接，不重算判定 */
  preview: string[]
  refresh: () => Promise<void>
}

function timeOf(ms: number): string {
  if (!ms) {
    return '未知时间'
  }
  return new Date(ms).toLocaleString()
}

function previewOf(items: OperationLogItem[]): string[] {
  return items.slice(0, 3).map(item => `${timeOf(item.createTime)} · ${item.resourceKey} · ${item.action} · ${item.targetLabel ?? ''}`)
}

export function useOperationLogReport(): OperationLogState {
  const state = shallowReactive<OperationLogState>({
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
      const data = await dataResourceApi.getOperationLogs()
      state.items = Array.isArray(data) ? data : []
      state.count = state.items.length
      state.preview = previewOf(state.items)
      state.failed = false
      state.ready = true
    }
    catch {
      // 只标记失败，不清空上次结果：刷新失败不该把「已知有操作」擦成「看起来没人改过」
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
