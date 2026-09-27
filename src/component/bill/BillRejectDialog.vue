<script lang="ts" setup>
import type { IDialogActionProps } from '@/framework'
import { ElAlert, ElInput } from 'element-plus'
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
    width="500px"
    min-height="120px"
    @cancel="() => props.onCancel?.()"
    @confirm="() => props.onConfirm?.(rejectReason)"
  >
    <div class="k-reject-dialog">
      <ElAlert
        :closable="false"
        show-icon
        title="单据驳回提示"
        description="驳回后单据状态将变更，制单人可查阅驳回原因并进行修改再次提交。"
        type="warning"
        class="k-reject-dialog__alert"
      />
      <ElInput
        v-model="rejectReason"
        :rows="4"
        maxlength="200"
        show-word-limit
        placeholder="请输入详细的驳回原因..."
        type="textarea"
      />
    </div>
  </KDialog>
</template>

<style lang="scss" scoped>
.k-reject-dialog {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.k-reject-dialog__alert {
  margin-bottom: 2px;
}
</style>
