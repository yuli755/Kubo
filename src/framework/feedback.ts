/**
 * # 框架层·交互反馈（吐司 / 确认框 / 命令式弹窗 / 剪贴板）
 *
 * 对齐原库的三件套：
 * - `FeedbackUtil.toastSuccess('保存成功')`、`await FeedbackUtil.confirmWarning('是否确认删除？')`
 * - `DialogUtil.show(Editor, row)` —— 命令式打开一个「自带 ADialog 的组件」并等它确认，
 *   返回组件 `onConfirm` 传出的数据；取消则 resolve `undefined`
 * - `ClipboardUtil.copy(text)`
 *
 * 命令式弹窗依赖当前应用上下文（pinia / router / Element Plus 组件都要在），
 * 因此由 `installFramework(app)` 在 `main.ts` 里注入一次。
 */

import type { AppContext, Component, VNode } from 'vue'
import type { IJson } from './types'
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

/** 当前应用上下文（含 pinia/router/全局组件），由 installFramework 注入 */
let appContext: AppContext | null = null

/** 注入应用上下文（内部使用：`main.ts` 安装框架时调用） */
export function setDialogAppContext(context: AppContext): void {
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

  /** 上传弹窗（上传能力后续按需接入，这里保持调用方契约） */
  static showUpload<T = unknown>(config?: unknown): Promise<T> {
    void config
    return Promise.reject(new Error('上传弹窗尚未在框架层实现'))
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
