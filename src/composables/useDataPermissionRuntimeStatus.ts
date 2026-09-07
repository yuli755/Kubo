import type { DataPermissionRuntimeStatus, ResourceRuntimeStatus } from '@/api/system/types'
import { onScopeDispose, shallowReactive } from 'vue'
import { dataResourceApi } from '@/api/system'

/**
 * # 数据权限灰度状态总览
 *
 * ## 它回答的是一个此前无处可查的问题
 * 「这个资源此刻到底是 shadow 还是 enforce？」——模式与 `enforce-resources` 名单在配置文件里，
 * 策略在数据库里，页面上原先两头都看不见。而"逐资源放开"这一步猜错的代价直接落在生产：
 * 把还没补 UPDATE 策略的资源放进名单，保存就会开始报错。
 *
 * ## 这里没有任何判定
 * `effectiveMode` 由后端 `DataPermissionProperties#effectiveMode` 给出（`mode=enforce` 时名单是
 * "例外清单"，`mode=shadow` 时才是"提前放开清单"——这条规则只有一份）。前端若自己判
 * `enforceResources.includes(key)`，就等于抄了第二份名单语义：后端一改（比如加租户维度的名单），
 * 面板立刻开始说谎，而管理员是照着面板做放开决策的。所以本文件只做两件事：拼接摘要、数条数。
 *
 * ## 失败取向
 * 与另外几份自查一致：失败只置 `failed=true`，不清空已有数据、也不假装"全部安全"。
 */

export interface DataPermissionRuntimeStatusState {
  loading: boolean
  failed: boolean
  ready: boolean
  status: DataPermissionRuntimeStatus | null
  /** 顶部一行摘要：全局模式 + 名单 + 两个放宽开关 */
  summary: string
  /** 有后端提示的资源数 */
  pendingCount: number
  /** 前若干条提示预览（`key：第一条提示`） */
  notePreview: string[]
  /** 派生出了资源键、但库里没登记的服务键 */
  unmanaged: string[]
  refresh: () => Promise<void>
}

const YES = '开'
const NO = '关'

/**
 * 只拼接，不解释：数组顺序、条数上限都是显示问题，不是判据
 */
function summarize(status: DataPermissionRuntimeStatus | null): string {
  if (!status) {
    return ''
  }
  const list = status.enforceResources.length
    ? status.enforceResources.join('、')
    : '（空）'
  const flags = `写侧行级保护 ${status.rowWriteEnabled ? YES : NO}、部门层级展开 ${status.hierarchyDepartmentEnabled ? YES : NO}、未登记 ${status.denyUnregistered ? '拒绝' : '放行'}`
  return `全局模式 ${status.globalMode}；enforce 名单 ${status.enforceResources.length} 项：${list}。${flags}`
}

function notesOf(items: ResourceRuntimeStatus[]): { count: number, preview: string[] } {
  const withNotes = items.filter(item => item.notes.length > 0)
  return {
    count: withNotes.length,
    preview: withNotes.slice(0, 3).map(item => `${item.resourceKey}：${item.notes[0]}`),
  }
}

export function useDataPermissionRuntimeStatus(): DataPermissionRuntimeStatusState {
  const state = shallowReactive<DataPermissionRuntimeStatusState>({
    loading: false,
    failed: false,
    ready: false,
    status: null,
    summary: '',
    pendingCount: 0,
    notePreview: [],
    unmanaged: [],
    refresh: async () => undefined,
  })

  const refresh = async (): Promise<void> => {
    state.loading = true
    try {
      const data = await dataResourceApi.getRuntimeStatus()
      state.status = data ?? null
      state.summary = summarize(state.status)
      const notes = notesOf(state.status?.resources ?? [])
      state.pendingCount = notes.count
      state.notePreview = notes.preview
      state.unmanaged = state.status?.unmanagedServiceKeys ?? []
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
    // 目前没有订阅要清理；留这一行提醒后来者：这里若加轮询，必须随作用域释放
  })

  return state
}
