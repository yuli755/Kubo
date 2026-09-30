<script lang="ts" setup>
import type { IDialogActionProps } from '@/framework'
import { ElButton, ElDialog } from 'element-plus'
import { computed } from 'vue'

/**
 * # KDialog —— 命令式弹窗的通用外壳（替代 airpower `ADialog`）
 *
 * ## 它解决什么
 * 存量页面的弹窗有两种打开方式：`DialogUtil.show(View, param)`（命令式，自己弹自己关）
 * 和 `<ADialog v-model="visible">`（声明式，页面自己管显隐）。两者对组件的 props 要求不同，
 * 于是每个弹窗都要写一遍外壳。这里把它收敛成一个组件：
 *
 * - **命令式**：`DialogUtil.show(BillRejectDialog, '驳回')` 会传 `param/onConfirm/onCancel`
 *   （见 `framework/feedback.ts` 的 `IDialogActionProps`）。本组件默认 `modelValue = true`，
 *   点取消/关窗时自动调 `onCancel`；
 * - **声明式**：页面 `v-model="visible"` + `@confirm` 使用。
 *
 * ## confirm 走哪条路（重要，踩过坑）
 * **只 `emit`，不再手动调 `props.onConfirm`**。若再手动调一次，外层用 `@confirm` 时就会
 * **回调两次**；若改成"有监听就 emit、没监听才调 prop"，那这个判定本身就靠不住
 * （见下面第 2 条：连字符写法会把处理器塞进 props，看着像"有监听"其实不是监听）。
 *
 * ### 两种接线契约（本项目并存，别混）
 * 1. **emit-only 型** —— 组件只 `emit('confirm', data)`，自己不碰 `props.onConfirm`。
 *    本组件、`KSelector`、`wms/inventory/component/selector.vue` 都是这一型。
 *    ⇒ 外层**必须写 `@confirm` / `@cancel`**。`@confirm="h"` 编译成驼峰键 `onConfirm`，
 *    `emit('confirm')` 恰好按 `toHandlerKey('confirm') = 'onConfirm'` 去 `vnode.props`
 *    里找，能命中。
 * 2. **props-call 型** —— 组件内部自己写 `@confirm="() => props.onConfirm?.(data)"`，
 *    把 KDialog 的 emit 接住再转调 props（如 `bill/BillRejectDialog.vue:29`、
 *    `bill/BillAddFinishDialog.vue:32`）。这一型对外暴露的就是 `IDialogActionProps`
 *    这份 props 契约，所以外层写 `:on-confirm="h"` 是**对的**：Vue 解析 props 时会对
 *    原始键做 camelize，`on-confirm` → `onConfirm` → 落进 `props.onConfirm`。
 *
 * ⚠️ 把第 2 型的写法用到第 1 型组件上会**静默失效**：`:on-confirm="h"` 编译成连字符键
 * `on-confirm`，而 `emit('confirm')` 只找驼峰键 `onConfirm`（emit 不会 camelize
 * `vnode.props` 的键），于是处理器被当成普通 attr 收下、永远不被调用，且**不报错**，
 * 表现为「弹窗点了确定但页面没反应」。批 0 的测试抓过一次；出库「确认出库」与移库
 * 挑选库存又各栽一次（`wms/output/component/addFinishEditor.vue`、
 * `wms/move/component/editor.vue`，均已改为 `@confirm`）。
 * ⇒ 记忆口诀：**看组件内部有没有 `props.onConfirm?.()` —— 有就写 `:on-confirm`，
 * 没有就写 `@confirm`。**
 *
 * 数据回传由**用它的组件**决定（弹窗内容在它手里）：
 * ```vue
 * <KDialog @confirm="props.onConfirm?.(rejectReason)" />
 * ```
 */
const props = withDefaults(defineProps<IDialogActionProps & {
  /** 显隐（命令式打开时不传，默认即打开） */
  modelValue?: boolean
  /** 标题 */
  title?: string
  /** 宽度 */
  width?: string
  /** 主体最小高度（与原 `ADialog` 的 min-height 对齐，避免短内容弹窗抖动） */
  minHeight?: string
  /** 确认按钮文案 */
  confirmText?: string
  /** 取消按钮文案 */
  cancelText?: string
  /** 确认按钮禁用 */
  disableConfirm?: boolean
  /** 隐藏取消按钮（如审批类只给「确认」） */
  hideCancel?: boolean
  /** 隐藏底部按钮区（内容自带操作时用） */
  hideFooter?: boolean
}>(), {
  modelValue: true,
  title: '',
  width: '520px',
  minHeight: '80px',
  confirmText: '确定',
  cancelText: '取消',
  disableConfirm: false,
  hideCancel: false,
  hideFooter: false,
})

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'confirm'): void
  (event: 'cancel'): void
}>()

defineSlots<{
  /** 弹窗内容 */
  default?: () => unknown
  /** 底部按钮区 */
  footer?: (props: { confirm: () => void, cancel: () => void, disabled: boolean }) => unknown
}>()

const visible = computed({
  get: () => props.modelValue ?? true,
  set: value => emit('update:modelValue', value),
})

function confirm(): void {
  if (props.disableConfirm)
    return
  visible.value = false
  // emit 会派发到外层的 `@confirm` 监听或 `:on-confirm` 回调（两者是同一个 vnode prop），
  // 不要再手动调 `props.onConfirm`（会回调两次）
  emit('confirm')
}

function cancel(): void {
  visible.value = false
  emit('cancel')
}
</script>

<template>
  <ElDialog
    v-model="visible"
    :close-on-click-modal="false"
    :title="title"
    :width="width"
    append-to-body
    destroy-on-close
    @close="cancel"
    @keydown.ctrl.enter="confirm"
    @keydown.meta.enter="confirm"
  >
    <div
      class="k-dialog__body"
      :style="{ minHeight }"
    >
      <slot />
    </div>
    <template
      v-if="!hideFooter"
      #footer
    >
      <slot
        :cancel="cancel"
        :confirm="confirm"
        :disabled="disableConfirm"
        name="footer"
      >
        <ElButton
          v-if="!hideCancel"
          @click="cancel"
        >
          {{ cancelText }}
        </ElButton>
        <ElButton
          :disabled="disableConfirm"
          type="primary"
          @click="confirm"
        >
          {{ confirmText }}
        </ElButton>
      </slot>
    </template>
  </ElDialog>
</template>

<style lang="scss" scoped>
.k-dialog__body {
  padding: 4px 0;
}
</style>
