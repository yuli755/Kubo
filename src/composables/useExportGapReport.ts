import { onScopeDispose, shallowReactive } from 'vue'
import { dataResourceApi } from '@/api/system'

/**
 * # 导出授权缺口自查报表
 *
 * ## 为什么需要它
 * enforce 下 `ExportBypassGuard` 的规则是"被遮蔽的字段**每一个**都要有 EXPORT 明文授权，
 * 缺一条就整份拒绝"。于是最常见的坏配置长成这样：QUERY 遮蔽配了、EXPORT 授权忘了。
 * 它在策略表里**看起来是完整的**（两类行都在），代价却要由用户点导出时撞 403 来暴露。
 * 本文件把那件"看不见的漏配"变成资源列表上的一行提示。
 *
 * ## 为什么这里没有判定逻辑
 * 缺口结论来自后端 `FieldPolicyGapAnalyzer`（与 guard 同一份角色覆盖语义）。
 * 前端若照着 `allowPlaintext` 自己再算一遍，就成了第二套真相 —— 而这条提示一旦骗人，
 * 管理员会开始无视它，比不加提示更糟。这里只做：取数、按资源聚合成可渲染的摘要、失败沉默。
 *
 * ## 失败取向：沉默（并把失败本身显式化）
 * `failed=true` 时调用方应当显示"未能取得自查结果"，而不是"没有缺口"。
 * 这一条与 `useMaskPolicy` 的取向相同：**"不知道"永远不能渲染成"没问题"**。
 * 之所以还要额外暴露 `failed`：本接口是 404 高发区（前后端路径各写一遍），
 * 沉默如果没有出口，就会把一个失效的功能读成一个绿色的功能。
 */

export interface GapSummary {
  /** 资源数（有缺口的资源个数） */
  resources: number
  /** 缺口条目总数 */
  entries: number
  /** 前几条，用于列表页直接点名 */
  preview: string[]
}

interface ExportGapState {
  loading: boolean
  failed: boolean
  ready: boolean
  byResource: Record<string, unknown[]>
  summary: GapSummary
  refresh: () => Promise<void>
}

function summarize(byResource: Record<string, unknown[]>): GapSummary {
  const keys = Object.keys(byResource ?? {})
  let entries = 0
  const preview: string[] = []
  for (const key of keys) {
    const list = byResource[key] ?? []
    entries += list.length
    if (preview.length < 3) {
      preview.push(`${key}（${list.length} 处）`)
    }
  }
  return { resources: keys.length, entries, preview }
}

export function useExportGapReport(): ExportGapState {
  const state = shallowReactive<ExportGapState>({
    loading: false,
    failed: false,
    ready: false,
    byResource: {},
    summary: { resources: 0, entries: 0, preview: [] },
    refresh: async () => undefined,
  })

  const refresh = async (): Promise<void> => {
    state.loading = true
    try {
      const data = await dataResourceApi.getExportGaps()
      state.byResource = data ?? {}
      state.summary = summarize(state.byResource)
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
    // 目前没有订阅的定时器/监听器；留这一行是为了提醒后来者：这里若要加重试，必须随作用域清理
  })

  return state
}
