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
 * **只 `emit`，不再手动调 `props.onConfirm`**。原因：`@confirm="h"` 与 `:on-confirm="h"`
 * 在 Vue 里是同一个东西（都会进 `vnode.props.onConfirm`），`emit('confirm')` 会把它派发到 ——
 * 两种写法本来就同一条路。若再手动 `props.onConfirm?.()` 就会**回调两次**；
 * 若改成"有监听就 emit、没监听才调 prop"，则外层用 `:on-confirm` 传处理器时
 * `vnode.props.onConfirm` 也非空 → 判定失效、回调一次都不走（弹窗点了确定却没反应）。
 *
 * 数据回传由**用它的组件**决定（弹窗内容在它手里）：
 * ```vue
 * <KDialog @confirm="props.onConfirm?.(rejectReason)" />
 * ```
 *
 * ⚠️ 外层**必须写 `@confirm` / `@cancel`**，不要写 `:on-confirm="handler"`。
 * 后者在编译产物里是 kebab 键 `on-confirm`，而 `emit('confirm')` 只在 `vnode.props`
 * 里找 `onConfirm` / camelize 后的键 —— 找不到就**静默不派发**，表现为
 * 「弹窗点了确定但页面没反应」，且不报错（批 0 的测试就是这么抓出来的）。
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
