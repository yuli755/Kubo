import type { InertPolicyItem } from '@/api/system/types'
import { onScopeDispose, shallowReactive } from 'vue'
import { dataResourceApi } from '@/api/system'

/**
 * # 「没人读」的列级策略盘点
 *
 * ## 它管的是存量，不是防呆
 * 策略编辑器的动作提示（`inertAction`）拦得住**新配**的错；但库里可能早就躺着几十条
 * `actionName=DETAIL` 的列级策略 —— 保存成功、列表可见、界面无变化，没有任何失败信号，
 * 于是谁都不会想起它们。没有这次盘点，它们就会一直躺在"已配置"的统计里。
 *
 * ## 为什么和 `useExportGapReport` 分成两个文件而不是合并
 * 两个端点回答的是两个问题（"该补什么" / "该清什么"），合并成一个 composable 会让
 * "其中一个取数失败"变成"整块都不显示"。分开之后，失败互不牵连，各自也能沉默。
 *
 * ## 失败取向：沉默，但把失败显式化
 * `failed=true` 时调用方应显示"未取得结果"，而不是"没有孤儿策略"。
 * 与 `useMaskPolicy` / `useExportGapReport` 同一条规矩：**"不知道"永远不能渲染成"没问题"**。
 */

export interface InertPolicyState {
  loading: boolean
  failed: boolean
  ready: boolean
  items: InertPolicyItem[]
  /** 条数，页面直接用它决定要不要显示 */
  count: number
  /** 前几条的人话摘要（"资源 · 字段（动作/角色）"），只是拼接，不重算判定 */
  preview: string[]
  refresh: () => Promise<void>
}

function previewOf(items: InertPolicyItem[]): string[] {
  return items.slice(0, 3).map(item => `${item.resourceKey} · ${item.fieldName}（${item.actionName}/${item.roleCode}）`)
}

export function useInertPolicyReport(): InertPolicyState {
  const state = shallowReactive<InertPolicyState>({
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
      const data = await dataResourceApi.getInertPolicies()
      state.items = Array.isArray(data) ? data : []
      state.count = state.items.length
      state.preview = previewOf(state.items)
      state.failed = false
      state.ready = true
    }
    catch {
      // 只标记失败，不清空上次结果：刷新失败不该把"已知有问题"擦成"看起来干净"
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
