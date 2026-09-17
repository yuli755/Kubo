<script lang="ts" setup>
import type { IDialogActionProps } from '@/framework'
import { ElInput } from 'element-plus'
import { ref } from 'vue'
import KDialog from '@/component/framework/KDialog.vue'

/**
 * # 添加完成数量弹窗
 *
 * 单据明细的「本次完成数量」输入框，由 `DialogUtil.show(BillAddFinishDialog, 待完成数量)` 打开，
 * 宿主拿到输入的数字（`onConfirm(num)`）后调服务接口。
 *
 * 批 0 迁移：原来用 airpower 的 `ADialog + DialogProps`，现在用 `@/framework` 的
 * `IDialogActionProps + KDialog` —— **props 契约不变**（`param` / `onConfirm` / `onCancel`），
 * 所以还没迁到新底座的 wms / channel / asset 单据页继续这样打开它，行为一致。
 */
const props = withDefaults(defineProps<IDialogActionProps<number>>(), {
  param: 0,
})

const num = ref(props.param ?? 0)
</script>

<template>
  <KDialog
    :disable-confirm="num <= 0"
    confirm-text="添加完成"
    min-height="200px"
    title="添加完成数量"
    @cancel="() => props.onCancel?.()"
    @confirm="() => props.onConfirm?.(num)"
  >
    <ElInput
      v-model="num"
      clearable
      placeholder="请输入本次完成数量..."
      type="number"
    />
  </KDialog>
</template>
