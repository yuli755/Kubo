import type { OutboxDeadItem } from '@/api/system/types'
import { onScopeDispose, shallowReactive } from 'vue'
import { dataResourceApi } from '@/api/system'

/**
 * # 权限变更死信盘点（`permission_change_outbox` 的 DEAD 行）
 *
 * ## 它回答的问题
 * 权限变更走「落 Outbox + 实时 Redis 广播」双路径；Dispatcher 连续 `MAX_RETRY` 次重投失败会把
 * 事件置为 `DEAD`（死信），意味着这条「缓存该失效了」的通知可能没送到各节点。放开 enforce 之后，
 * 死信不处理 = 某个角色改了策略、某些节点还在用旧缓存。这个报表把死信摆到数据资源管理页，
 * 并给出「重放 / 重放全部 / 丢弃」三个人工处置动作。
 *
 * ## 为什么这里没有任何判定逻辑
 * 「什么算死信」「重放后状态怎么走」全部在后端（`PermissionChangeOutboxStatus` /
 * `OutboxDispatchStep.markReplay`），前端只搬运与触发，不重算状态迁移。列表里不展示 payload 原文
 * （事件 JSON 里可能含策略明细，管理端已经可见，这里不再放大）。
 *
 * ## 失败取向：与拒绝事件盘点同款
 * 后端在「取不到表 / 无死信」时返回空数组，前端分不清「空」是「没有死信」还是「取不到」，
 * 因此把「请求抛错」标记为 `failed`，调用方显示「未能取得死信列表」；照实显示，不假装干净。
 * 重放 / 丢弃失败时把异常抛回调用方（由页面 toast 提示），不在这里吞掉 —— 特权动作失败必须可见。
 */

export interface OutboxDeadState {
  loading: boolean
  failed: boolean
  ready: boolean
  items: OutboxDeadItem[]
  /** 死信条数（后端最多给 100），页面据此决定要不要显示整块 */
  count: number
  /** 前几条的人话摘要（「事件类型 · 重试次数 · 最后错误」），只是拼接，不重算判定 */
  preview: string[]
  refresh: () => Promise<void>
  replay: (eventKey: string) => Promise<void>
  replayAll: () => Promise<void>
  discard: (eventKey: string) => Promise<void>
}

function previewOf(items: OutboxDeadItem[]): string[] {
  return items.slice(0, 3).map(item => `${item.eventType} · 重试 ${item.retryCount} 次 · ${item.lastError ?? '无错误信息'}`)
}

export function useOutboxDead(): OutboxDeadState {
  const state = shallowReactive<OutboxDeadState>({
    loading: false,
    failed: false,
    ready: false,
    items: [],
    count: 0,
    preview: [],
    refresh: async () => undefined,
    replay: async () => undefined,
    replayAll: async () => undefined,
    discard: async () => undefined,
  })

  const refresh = async (): Promise<void> => {
    state.loading = true
    try {
      const data = await dataResourceApi.getOutboxDead()
      state.items = Array.isArray(data) ? data : []
      state.count = state.items.length
      state.preview = previewOf(state.items)
      state.failed = false
      state.ready = true
    }
    catch {
      // 只标记失败，不清空上次结果：刷新失败不该把「已知有死信」擦成「看起来没有」
      state.failed = true
    }
    finally {
      state.loading = false
    }
  }

  const replay = async (eventKey: string): Promise<void> => {
    await dataResourceApi.replayOutbox(eventKey)
    await refresh()
  }

  const replayAll = async (): Promise<void> => {
    await dataResourceApi.replayAllOutbox()
    await refresh()
  }

  const discard = async (eventKey: string): Promise<void> => {
    await dataResourceApi.discardOutbox(eventKey)
    await refresh()
  }

  state.refresh = refresh
  state.replay = replay
  state.replayAll = replayAll
  state.discard = discard
  void refresh()
  onScopeDispose(() => {
    // 目前没有订阅要清理；留这一行提醒后来者：这里若加轮询/重试，必须随作用域释放
  })

  return state
}
