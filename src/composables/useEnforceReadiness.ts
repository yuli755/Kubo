import type { EnforceReadiness } from '@/api/system/types'
import { onScopeDispose, shallowReactive } from 'vue'
import { dataResourceApi } from '@/api/system'

/**
 * # 逐资源放开的前置检查（只读）
 *
 * ## 它挡的是一个不对称的坑
 * 把资源写进 `enforce-resources` 名单，少配一项与多配一项的后果都发生在放开之后：前者是"以为有防护"
 * （查询侧没策略 → 一行都不过滤），后者是"业务当场坏掉"（没 UPDATE 策略 → 保存被拒；维度绑不上列 →
 * 列表查空）。放开的动作只要改一行 YAML，而检查要数四张表，人肉数必然开始漏 —— 所以判据放在后端
 * （`DataResourceService.readinessChecks`），这里只把结论摆出来。
 *
 * ## 这里没有任何判定
 * `ready`、`blockers` 都是后端算好的。**不得**在本文件里根据 `checks.length` / `blockers.length`
 * 反推"能不能放开"：影子证据那条判据（必须存在 EXPECTED_FILTER 样本，且 ANOMALY/ERROR 为 0，
 * 读不到差异表按"无证据"处理）在前端根本无从复现，抄一份只会抄错。计数只用于显示。
 *
 * ## 失败取向
 * 与其它自查一致：失败只置 `failed`，不清空已有数据，也不显示成"全部就绪"。
 * 这一条尤其重要 —— 这里显示"全部就绪"会直接诱导一次错误的放开。
 */

export interface EnforceReadinessState {
  loading: boolean
  failed: boolean
  /** 数据是否已成功取到（不是"资源是否就绪"） */
  fetched: boolean
  /** 已登记资源数 */
  total: number
  /** 满足放开前置的资源数 */
  readyCount: number
  /** 未就绪资源的前若干条预览：`key：第一条阻塞原因` */
  preview: string[]
  /** 原始结论（编辑器等其它区块可用） */
  items: EnforceReadiness[]
  refresh: () => Promise<void>
}

const PREVIEW_LIMIT = 3

/**
 * 只数数、只拼接：哪些资源就绪、缺什么，全部以回传的 ready / blockers 为准
 */
function summarize(items: EnforceReadiness[]): { total: number, readyCount: number, preview: string[] } {
  const readyCount = items.filter(item => item.ready).length
  return {
    total: items.length,
    readyCount,
    preview: items
      .filter(item => !item.ready && item.blockers.length > 0)
      .slice(0, PREVIEW_LIMIT)
      .map(item => `${item.resourceKey}：${item.blockers[0]}`),
  }
}

export function useEnforceReadiness(): EnforceReadinessState {
  const state = shallowReactive<EnforceReadinessState>({
    loading: false,
    failed: false,
    fetched: false,
    total: 0,
    readyCount: 0,
    preview: [],
    items: [],
    refresh: async () => undefined,
  })

  const refresh = async (): Promise<void> => {
    state.loading = true
    try {
      const data = await dataResourceApi.getEnforceReadiness()
      // 单资源接口返回对象、全部返回数组；面板只走"全部"，这里两种都兜住以免后端调整时前端炸
      state.items = Array.isArray(data) ? data : [data]
      const summary = summarize(state.items)
      state.total = summary.total
      state.readyCount = summary.readyCount
      state.preview = summary.preview
      state.failed = false
      state.fetched = true
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
