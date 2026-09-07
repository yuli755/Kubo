import type { DimensionBindingRiskItem } from '@/api/system/types'
import { onScopeDispose, shallowReactive } from 'vue'
import { dataResourceApi } from '@/api/system'

/**
 * # 维度可绑定性盘点
 *
 * ## 它和"行级策略永不命中"是两层事实
 * 那份盘的是"这一行策略会不会被读到"（动作名、主体类型写错）；这份盘的是
 * **就算读到了，它要的列在实体上存不存在**。运行期对两者的处理一样安静：本该产生的条件绑不上列，
 * enforce 下退化成恒假（该主体一行也查不到）、shadow 下等价于没配，界面上既不报错也不变黄。
 * 修法也不同：这里要改的是**资源的列登记**（或补关联路径支持），去改策略行是没用的。
 *
 * ## 这里没有任何判定
 * 类别（kind）与原因（detail）都由后端 `DimensionBindingAnalyzer` 给出，本文件只数条数、拼摘要。
 * 前端若自己判"哪个维度该有哪一列"，就等于抄了第二份实体形状 —— 实体一改，面板立刻开始说谎。
 *
 * ## 失败取向
 * 与另两份自查一致：失败时只置 `failed=true`，不清空也不假装"没有风险"。
 */

export interface DimensionBindingRiskState {
  loading: boolean
  failed: boolean
  ready: boolean
  items: DimensionBindingRiskItem[]
  count: number
  /** 前几条摘要（资源 · 维度（类别）），仅拼接，不解释语义 */
  preview: string[]
  refresh: () => Promise<void>
}

function previewOf(items: DimensionBindingRiskItem[]): string[] {
  return items.slice(0, 3).map(item => `${item.resourceKey} · ${item.subjectType ?? '(整表)'}（${item.kind}）`)
}

export function useDimensionBindingRiskReport(): DimensionBindingRiskState {
  const state = shallowReactive<DimensionBindingRiskState>({
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
      const data = await dataResourceApi.getDimensionBindingRisks()
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
