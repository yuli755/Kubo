<script lang="ts" setup>
import type { IDialogActionProps } from '@/framework'
import { ElInput } from 'element-plus'
import { ref } from 'vue'
import KDialog from '@/component/framework/KDialog.vue'

/**
 * # 单据驳回弹窗
 *
 * `DialogUtil.show(BillRejectDialog, '驳回')` 打开，宿主拿到驳回原因字符串
 * （`onConfirm(reason)`）后调服务接口。第二个参数是标题（不同单据文案不同）。
 *
 * 批 0 迁移：airpower `ADialog + DialogProps` → `IDialogActionProps + KDialog`，契约不变。
 */
const props = withDefaults(defineProps<IDialogActionProps<string>>(), {
  param: '驳回',
})

const rejectReason = ref('')
</script>

<template>
  <KDialog
    :disable-confirm="rejectReason === ''"
    :title="props.param ?? '驳回'"
    min-height="200px"
    @cancel="() => props.onCancel?.()"
    @confirm="() => props.onConfirm?.(rejectReason)"
  >
    <ElInput
      v-model="rejectReason"
      :rows="3"
      placeholder="请输入驳回原因..."
      type="textarea"
    />
  </KDialog>
</template>
