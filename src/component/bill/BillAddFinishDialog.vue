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
    :disable-confirm="Number(num) <= 0"
    confirm-text="添加完成"
    width="480px"
    min-height="100px"
    title="添加完成数量"
    @cancel="() => props.onCancel?.()"
    @confirm="() => props.onConfirm?.(Number(num))"
  >
    <div class="k-finish-dialog">
      <div
        v-if="props.param && props.param > 0"
        class="k-finish-dialog__tip"
      >
        <span>建议待完成数量：<strong>{{ props.param }}</strong></span>
      </div>
      <ElInput
        v-model="num"
        clearable
        placeholder="请输入本次完成数量..."
        type="number"
      />
    </div>
  </KDialog>
</template>

<style lang="scss" scoped>
.k-finish-dialog {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.k-finish-dialog__tip {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
  padding: 8px 12px;
  border-radius: var(--el-border-radius-base);
  border: 1px solid var(--el-border-color-lighter);

  strong {
    color: var(--el-color-primary);
    font-weight: 600;
  }
}
</style>
