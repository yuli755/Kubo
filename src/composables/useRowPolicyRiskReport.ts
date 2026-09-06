import type { RowPolicyRiskItem } from '@/api/system/types'
import { onScopeDispose, shallowReactive } from 'vue'
import { dataResourceApi } from '@/api/system'

/**
 * # 行级策略"永不命中"盘点
 *
 * ## 为什么这一份比列级那份更要命
 * 列级策略写错动作名，最坏结果是"某一列照旧显示"；行级规则不命中，最坏结果是
 * **该主体在这个资源上完全没有过滤**，而策略列表里它显示"已启用"。
 * 反过来的方向也一样糟：如果那条不命中的行是该主体在此资源上的唯一策略，
 * enforce 下它会落到默认拒绝 —— 症状变成"这个人突然查不到数据"。两种都值得在管理端数出来。
 *
 * ## 这里没有任何判定
 * 类别（kind）与原因（detail）都由后端 `RowPolicyRiskAnalyzer` 给出，本文件只数条数、拼摘要。
 * 前端若自己判"哪个动作算合法 / 哪种主体算可匹配"，就等于抄了第三份消费集 ——
 * 运行期那两处代码一改，面板立刻开始说谎，而说谎的面板比没有面板更糟。
 *
 * ## 失败取向
 * 与前两份自查一致：失败时 `failed=true`，调用方显示"未能取得结果"，绝不显示成"没有风险"。
 */

export interface RowPolicyRiskState {
  loading: boolean
  failed: boolean
  ready: boolean
  items: RowPolicyRiskItem[]
  count: number
  /** 前几条摘要（资源 · 编码（类别）），仅拼接 */
  preview: string[]
  refresh: () => Promise<void>
}

function previewOf(items: RowPolicyRiskItem[]): string[] {
  return items.slice(0, 3).map(item => `${item.resourceKey} · ${item.policyCode}（${item.kind}）`)
}

export function useRowPolicyRiskReport(): RowPolicyRiskState {
  const state = shallowReactive<RowPolicyRiskState>({
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
      const data = await dataResourceApi.getRowPolicyRisks()
      state.items = Array.isArray(data) ? data : []
      state.count = state.items.length
      state.preview = previewOf(state.items)
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
    // 目前没有订阅要清理；留这一行提醒后来者：这里若加轮询/重试，必须随作用域释放
  })

  return state
}
