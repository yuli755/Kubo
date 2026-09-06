import { onScopeDispose, shallowReactive } from 'vue'
import { dataFieldPolicyApi } from '@/api/system'

/**
 * # 导出可见性提示
 *
 * ## 它解决的到底是什么
 * 受限角色点"导出"时，enforce 下得到的是一句后端串（缺 EXPORT 明文授权），
 * 而且要等一次请求才发现。这个 composable 把同一判据的**结论**提前渲染成一行提示。
 *
 * ## 为什么这里没有一条判定逻辑
 * "哪些字段受限、某个字段能不能明文导出"是后端的结论（`ExportBypassGuard` 与
 * `ExportColumnMasker` 共用一份判据）。前端一旦自己实现 `allowPlaintext` 的比较，
 * 就有了第三份真相，而三份真相一定会漂移 —— 漂移的结果不是样式错，而是"提示骗人"。
 * 所以本文件只做三件事：取数、缓存、把结论翻译成人话。
 *
 * ## 失败取向：沉默
 * 取数失败时 `ready=false`，调用方应当什么都不提示。这里是**故意不阻断导出按钮**的：
 * 真正的闸门在后端 guard，把"网络抖一下"变成"导不出"是拿可用性换错觉；
 * 反过来若失败时提示"你可以导出"，则是在骗人。两种都比沉默更糟。
 *
 * ## 缓存
 * 与 `useMaskPolicy` 同一条理由：结论按用户解析，缓存不能只有 resourceKey。
 * 这里用"模块级 Map + 手动 refresh()"，不做身份指纹（本文件只服务一个提示，
 * 换身份时列表页会整页重挂，届时 refresh 自然发生）。
 */

export interface ExportVisibility {
  /** 受限字段名 → 是否可明文导出（后端结论） */
  fields: Record<string, boolean>
  /** 是否已取回（失败为 false，此时不应当渲染任何结论） */
  ready: boolean
}

interface State extends ExportVisibility {
  loading: boolean
  /** 未授权明文的受限字段：这些字段会让整份导出被拒 */
  blocked: string[]
  refresh: () => Promise<void>
}

const cache = shallowReactive(new Map<string, ExportVisibility>())
const inflight = new Map<string, Promise<void>>()

export function useExportVisibility(resourceKey: string): State {
  const state = shallowReactive<State>({
    fields: {},
    ready: false,
    loading: false,
    blocked: [],
    refresh: async () => undefined,
  })

  function render(snapshot: ExportVisibility | undefined): void {
    state.fields = snapshot?.fields ?? {}
    state.ready = snapshot?.ready ?? false
    state.blocked = Object.entries(state.fields)
      .filter(([, granted]) => granted !== true)
      .map(([field]) => field)
  }

  render(cache.get(resourceKey))

  const refresh = async (): Promise<void> => {
    if (inflight.has(resourceKey)) {
      await inflight.get(resourceKey)
      render(cache.get(resourceKey))
      return
    }
    state.loading = true
    const task = dataFieldPolicyApi
      .getMyExportVisibility(resourceKey)
      .then((fields) => {
        cache.set(resourceKey, { fields: fields ?? {}, ready: true })
      })
      .catch(() => {
        // 失败只让提示消失，不把"未知"渲染成任何结论
        cache.set(resourceKey, { fields: {}, ready: false })
      })
      .finally(() => {
        state.loading = false
        inflight.delete(resourceKey)
      })
    inflight.set(resourceKey, task)
    await task
    render(cache.get(resourceKey))
  }

  state.refresh = refresh
  if (!cache.has(resourceKey)) {
    void refresh()
    onScopeDispose(() => {
      inflight.delete(resourceKey)
    })
  }

  return state
}
