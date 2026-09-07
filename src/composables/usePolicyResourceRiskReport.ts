import type { PolicyResourceRiskItem } from '@/api/system/types'
import { onScopeDispose, shallowReactive } from 'vue'
import { dataResourceApi } from '@/api/system'

/**
 * # 「策略指向缺失 / 未启用资源」盘点
 *
 * ## 它与 `useRowPolicyRiskReport` 是同一张表的两种病
 * 那份回答「策略自身写错」（动作名 / 主体必填项）；这份回答「资源引用坏掉」——资源已删除
 * 或未启用，运行期没有任何请求会轮到这条策略，但它仍躺在策略列表里显示「已启用」。
 * 两者都要在策略页被数出来，修法却完全不同：这份是删策略 / 补登记 / 启用资源，
 * 不是去改动作名。
 *
 * ## 为什么条目里没有 resourceKey
 * 孤儿策略引用的资源 ID 已不存在，拿不到 key —— 后端故意不塞一个「猜测的」key 进来。
 * 前端只按后端给的 `detail` 原文显示，不要拿 `resourceId` 去 join 资源列表（那会把孤儿
 * 条目静默丢掉）。
 *
 * ## 这里没有任何判定
 * 类别（kind）与原因（detail）都由后端 `PolicyResourceReferenceAnalyzer` 给出，本文件只数条数、
 * 拼摘要。前端若自己判「什么算孤儿 / 什么算未启用」，就抄了第二份 findResource 语义。
 *
 * ## 失败取向
 * 与其余盘点一致：失败时 `failed=true`，调用方显示「未能取得结果」，绝不显示成「没有风险」。
 */

export interface PolicyResourceRiskState {
  loading: boolean
  failed: boolean
  ready: boolean
  items: PolicyResourceRiskItem[]
  count: number
  /** 前几条摘要（策略编码（类别）），仅拼接 */
  preview: string[]
  refresh: () => Promise<void>
}

function previewOf(items: PolicyResourceRiskItem[]): string[] {
  return items.slice(0, 3).map(item => `${item.policyCode}（${item.kind}）`)
}

export function usePolicyResourceRiskReport(): PolicyResourceRiskState {
  const state = shallowReactive<PolicyResourceRiskState>({
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
      const data = await dataResourceApi.getPolicyResourceRisks()
      state.items = Array.isArray(data) ? data : []
      state.count = state.items.length
      state.preview = previewOf(state.items)
      state.failed = false
      state.ready = true
    }
    catch {
      // 只标记失败，不清空上次结果：刷新失败不该把「已知有坏引用」擦成「看起来干净」
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
