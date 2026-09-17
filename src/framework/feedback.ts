/**
 * # 框架层·交互反馈（吐司 / 确认框 / 命令式弹窗 / 剪贴板）
 *
 * 对齐原库的三件套：
 * - `FeedbackUtil.toastSuccess('保存成功')`、`await FeedbackUtil.confirmWarning('是否确认删除？')`
 * - `DialogUtil.show(Editor, row)` —— 命令式打开一个「自带 ADialog 的组件」并等它确认，
 *   返回组件 `onConfirm` 传出的数据；取消则 resolve `undefined`
 * - `ClipboardUtil.copy(text)`
 *
 * 命令式弹窗依赖当前应用上下文（pinia / router / Element Plus 组件都要在）：
 * 弹窗挂在**独立容器**里，是一棵新的组件树，不继承 `main.ts` 那个 app 的注入，
 * 所以必须先调一次 `setDialogAppContext(app._context)`（`main.ts` 里已接线）。
 *
 * ⚠️ 漏接的表现是弹窗**静默不开**（不报错、按钮点了没反应）：宿主里的 `useQuery` /
 * `usePermissionStore` 找不到注入，直接抛 `No 'queryClient' found in Vue context`，
 * 异常发生在 `DialogUtil.show()` 内部的 `render()` 里，调用方 `void` 掉就再也看不见。
 * 批 5（channel）的「查看详情」踩过这个坑。
 */

import type { AppContext, Component, VNode } from 'vue'
import type { IJson } from './types'
import type { IUploadConfig } from './upload'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createVNode, defineComponent, h, render } from 'vue'
import { WebConfig } from './config'

/** 反馈类型（与原库同名） */
export type FeedbackType = 'success' | 'warning' | 'error' | 'info'

// ────────────────────────────── 吐司与确认框 ──────────────────────────────

export class FeedbackUtil {
  /** 默认吐司时长 */
  static defaultToastDuration = 3000

  static toastWarning(message: string): void {
    this.showToast(message, 'warning')
  }

  static toastInfo(message: string): void {
    this.showToast(message, 'info')
  }

  static toastSuccess(message: string): void {
    this.showToast(message, 'success')
  }

  static toastError(message: string): void {
    this.showToast(message, 'error')
  }

  static showToast(message: string, type?: FeedbackType, duration = this.defaultToastDuration): void {
    ElMessage({ message, type, plain: true, duration })
  }

  /** 确认框（点取消会 reject，调用方用 await 即可「确认后才继续」） */
  static async showConfirm(message: string, title = '请确认', type?: FeedbackType): Promise<void> {
    await ElMessageBox.alert(message, title, {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      showCancelButton: true,
      type,
      closeOnClickModal: false,
      draggable: true,
    })
  }

  static confirmSuccess(message: string, title?: string): Promise<void> {
    return this.showConfirm(message, title, 'success')
  }

  static confirmError(message: string, title?: string): Promise<void> {
    return this.showConfirm(message, title, 'error')
  }

  static confirmWarning(message: string, title?: string): Promise<void> {
    return this.showConfirm(message, title, 'warning')
  }

  static confirmInfo(message: string, title?: string): Promise<void> {
    return this.showConfirm(message, title, 'info')
  }

  /** 提示框（只有一个「知道了」） */
  static async showAlert(message: string, title = '提示', type?: FeedbackType): Promise<void> {
    await ElMessageBox.alert(message, title, {
      confirmButtonText: '知道了',
      type,
      draggable: true,
    })
  }

  static alertSuccess(message: string, title?: string): Promise<void> {
    return this.showAlert(message, title, 'success')
  }

  static alertError(message: string, title?: string): Promise<void> {
    return this.showAlert(message, title, 'error')
  }

  static alertWarning(message: string, title?: string): Promise<void> {
    return this.showAlert(message, title, 'warning')
  }

  static alertInfo(message: string, title?: string): Promise<void> {
    return this.showAlert(message, title, 'info')
  }
}

// ────────────────────────────── 命令式弹窗 ──────────────────────────────

/**
 * # 命令式弹窗的入参契约（`DialogUtil.show/select/selectList` 传给弹窗组件的 props）
 *
 * 弹窗组件声明这几个 props 就能被 `DialogUtil` 打开，**不需要知道是谁打开的**：
 * - 存量 airpower 页面用 `@airpower/web` 的 `DialogUtil.show(BillRejectDialog, '驳回')`；
 * - 新页面用 `@/framework` 的 `DialogUtil.show(...)`；
 * 两者传给组件的 props 形状一致（这也是批 0 能让共享弹窗"两边都活"的原因）。
 *
 * 典型写法（见 `src/component/framework/KDialog.vue` 与 `src/component/bill/*`）：
 * ```ts
 * const props = defineProps<IDialogActionProps<string>>()
 * props.onConfirm?.(value)   // 关窗并 resolve
 * ```
 */
export interface IDialogActionProps<P = unknown> {
  /** 打开弹窗时传入的参数（`DialogUtil.show(view, param)` 的第二个参数） */
  param?: P
  /** 多选场景（`DialogUtil.selectList`） */
  isMultiple?: boolean
  /** 已选项（选择器回显） */
  selectList?: unknown[]
  /** 确认回调（弹窗内调用它并传值，宿主据此 resolve） */
  onConfirm?: (data?: unknown) => void
  /** 取消回调（宿主 resolve undefined） */
  onCancel?: () => void
  /** 只回调不关闭（宿主忽略即可） */
  onCallback?: () => void
}

/** 当前应用上下文（含 pinia/router/全局组件），由 `main.ts` 调 `setDialogAppContext` 注入 */
let appContext: AppContext | null = null

/** 注入应用上下文（`main.ts` 在 `createApp` 之后调用；测试里传 `null` 可复位） */
export function setDialogAppContext(context: AppContext | null): void {
  appContext = context
}

interface IHostResult {
  data?: unknown
  cancelled?: boolean
}

/**
 * 宿主组件：把目标组件挂到独立容器里，接管 confirm/cancel 两个事件，
 * 关闭后卸载并 resolve。
 */
const DialogHost = defineComponent({
  props: {
    view: { type: [Object, Function], required: true },
    param: { type: null as unknown as never, default: undefined },
    isMultiple: { type: Boolean, default: false },
    selectList: { type: Array as unknown as never, default: () => [] },
    onDone: { type: Function as unknown as never, required: true },
  },
  setup(props) {
    let finished = false
    const finish = (result: IHostResult) => {
      if (finished) {
        return
      }
      finished = true
      ;(props.onDone as unknown as (result: IHostResult) => void)(result)
    }
    return () => h(props.view as Component, {
      param: props.param,
      isMultiple: props.isMultiple,
      selectList: props.selectList,
      onConfirm: (data?: unknown) => finish({ data }),
      onCancel: () => finish({ cancelled: true }),
      // onCallback：弹窗内主动回调但不关闭，宿主忽略即可
      onCallback: () => {},
    })
  },
})

function openDialog<T>(view: Component, props: IJson): Promise<T | undefined> {
  return new Promise<T | undefined>((resolve) => {
    const container = document.createElement('div')
    document.body.appendChild(container)

    const dispose = (result: IHostResult) => {
      render(null, container)
      container.remove()
      resolve((result.cancelled ? undefined : result.data) as T | undefined)
    }

    const vnode: VNode = createVNode(DialogHost as never, { ...props, view, onDone: dispose })
    // 透传应用上下文：否则宿主里的组件拿不到 pinia/router，Element Plus 的主题也会丢
    if (appContext) {
      vnode.appContext = appContext
    }
    render(vnode, container)
  })
}

export class DialogUtil {
  /** 打开一个「自带 ADialog」的组件，等它确认后返回数据 */
  static show<T = unknown>(view: Component, param?: unknown): Promise<T> {
    return openDialog<T>(view, { param }) as Promise<T>
  }

  /** 单选：返回选中实体 */
  static select<E>(view: Component, param?: E): Promise<E> {
    return openDialog<E>(view, { param, isMultiple: false }) as Promise<E>
  }

  /** 多选：返回选中实体数组 */
  static selectList<E>(view: Component, selectList: E[] = [], param?: E): Promise<E[]> {
    return openDialog<E[]>(view, { param, isMultiple: true, selectList }) as Promise<E[]>
  }

  /**
   * 上传弹窗：选文件 → 上传 → 确定，成功时 resolve 上传记录（多选时是数组），取消 resolve `undefined`。
   *
   * ```ts
   * const file = await DialogUtil.showUpload({ category: FileCategory.CONTRACT_ATTACHMENT.key })
   * if (file) form.documentList.push(file)
   * ```
   *
   * 兼容旧契约：`{ data: { category }, entity: FileEntity }` 也能打开（`entity` 忽略）。
   *
   * ⚠️ 这里用**动态 import** 拿弹窗组件，不是为了省体积（虽然顺带省了），而是为了不制造
   * `framework/feedback.ts → component/framework/KUploadDialog.vue → KUpload.vue → @/framework`
   * 的**循环依赖**：框架层不应该在求值期就依赖组件层。
   */
  static async showUpload<T = unknown>(config?: IUploadConfig): Promise<T | undefined> {
    const { default: KUploadDialog } = await import('@/component/framework/KUploadDialog.vue')
    return openDialog<T>(KUploadDialog as Component, { param: config })
  }
}

// ────────────────────────────── 剪贴板 / 文件 ──────────────────────────────

export class ClipboardUtil {
  /** 复制文本到剪贴板（失败时给出降级提示，不吞异常） */
  static async copy(content: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(content)
      FeedbackUtil.toastSuccess('已复制到剪贴板')
    }
    catch {
      FeedbackUtil.toastError('当前浏览器不允许访问剪贴板，请手动复制')
    }
  }
}

export class WebFileUtil {
  /** 静态资源完整地址（后端返回相对路径时用） */
  static getStaticFileUrl(url?: string): string {
    if (!url) {
      return ''
    }
    if (/^https?:\/\//.test(url)) {
      return url
    }
    return `${WebConfig.staticUrl}${url.replace(/^\/+/, '')}`
  }
}
