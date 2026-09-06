import type { ExportGapItem } from '@/api/system/types'
import { onScopeDispose, shallowReactive } from 'vue'
import { dataResourceApi } from '@/api/system'

/**
 * # 导出授权缺口自查报表
 *
 * ## 为什么需要它
 * enforce 下 `ExportBypassGuard` 的规则是"被遮蔽的字段**每一个**都要有 EXPORT 明文授权，
 * 缺一条就整份拒绝"。于是最常见的坏配置长成这样：QUERY 遮蔽配了、EXPORT 授权忘了。
 * 它在策略表里**看起来是完整的**（两类行都在，只是配得不全），代价却要由用户点导出时撞 403 来暴露。
 * 本文件把那件"看不见的漏配"送到两个地方：资源列表页的汇总，以及策略编辑器里"当前这条字段"的即时提示。
 *
 * ## 为什么这里没有判定逻辑
 * 缺口结论来自后端 `FieldPolicyGapAnalyzer`（与 guard 同一份角色覆盖语义），这里只做两种搬运：
 * 按资源分组数个数、按 `(resourceId, fieldName)` 筛出与当前表单相关的条目。
 * 一旦前端照着 `allowPlaintext` 自己再算一遍，就成了第二套真相；而这条提示一旦骗人，
 * 管理员会开始无视它，比不加提示更糟。
 *
 * ## 为什么不做模块级缓存
 * 与 `useExportVisibility`（业务侧每次进列表查一次、可以复用）不同：这是管理员在**改配置**的界面。
 * 补完一条 EXPORT 授权后，若编辑器继续显示旧结果里的缺口，管理员会以为"改了没用"；
 * 反过来把已修好的缺口留着，下一次他就会开始怀疑整个面板。所以每次打开对话框都重新取。
 *
 * ## 失败取向：沉默，但把失败显式化
 * `failed=true` 时调用方应显示"未能取得自查结果"，而不是"没有缺口"。
 * 本接口还是 404 高发区（前后端路径各写一遍），所以拼写由 CI 对账（见 maskContract.spec.ts）。
 */

export interface GapSummary {
  /** 有缺口的资源个数 */
  resources: number
  /** 缺口条目总数 */
  entries: number
  /** 前几条"资源（几处）"，用于列表页直接点名 */
  preview: string[]
}

export interface ExportGapState {
  loading: boolean
  failed: boolean
  ready: boolean
  items: ExportGapItem[]
  summary: GapSummary
  refresh: () => Promise<void>
}

function summarize(items: ExportGapItem[]): GapSummary {
  const byResource = new Map<string, number>()
  for (const item of items) {
    byResource.set(item.resourceKey, (byResource.get(item.resourceKey) ?? 0) + 1)
  }
  const ordered = [...byResource.entries()].sort((a, b) => b[1] - a[1])
  return {
    resources: byResource.size,
    entries: items.length,
    preview: ordered.slice(0, 3).map(([key, count]) => `${key}（${count} 处）`),
  }
}

export function useExportGapReport(): ExportGapState {
  const state = shallowReactive<ExportGapState>({
    loading: false,
    failed: false,
    ready: false,
    items: [],
    summary: { resources: 0, entries: 0, preview: [] },
    refresh: async () => undefined,
  })

  const refresh = async (): Promise<void> => {
    state.loading = true
    try {
      const data = await dataResourceApi.getExportGaps()
      state.items = Array.isArray(data) ? data : []
      state.summary = summarize(state.items)
      state.failed = false
      state.ready = true
    }
    catch {
      // 只标记失败，不清空上次结果：刷新失败不该把"已知有缺口"擦成"看起来没缺口"
      state.failed = true
    }
    finally {
      state.loading = false
    }
  }

  state.refresh = refresh
  void refresh()
  onScopeDispose(() => {
    // 目前没有订阅的定时器/监听器；留这一行是为了提醒后来者：这里若加重试，必须随作用域清理
  })

  return state
}

/**
 * 取与某个 (资源, 字段) 相关的缺口条目
 *
 * 只做筛选，不判断"算不算缺口"：角色是否被 `default` 兜底覆盖，后端已经算在条目里了。
 */
export function gapsForField(items: ExportGapItem[], resourceId?: number, fieldName?: string): ExportGapItem[] {
  if (!fieldName || resourceId === undefined || resourceId === null) {
    return []
  }
  return items.filter(item => item.resourceId === resourceId && item.fieldName === fieldName)
}
