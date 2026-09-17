<script lang="ts" setup>
import type { IDialogActionProps, IUploadConfig, IUploadedFile, IUploadOption } from '@/framework'
import { ElAlert } from 'element-plus'
import { computed, ref } from 'vue'
import KDialog from './KDialog.vue'
import KUpload from './KUpload.vue'

/**
 * # KUploadDialog —— 命令式上传弹窗（`DialogUtil.showUpload()` 的内容体）
 *
 * 打开方式（见 `framework/feedback.ts`）：
 * ```ts
 * const file = await DialogUtil.showUpload({ category: FileCategory.CONTRACT_ATTACHMENT.key })
 * if (file) form.documentList.push(file)
 * ```
 *
 * 契约（兼容旧调用点）：
 * - 新写法：`showUpload({ category, accept, maxSizeMB, multiple })`
 * - 旧写法（airpower `IUploadProps`）：`showUpload({ data: { category }, entity: FileEntity })` ——
 *   `data.category` 会被读出来当成类别，`entity` 只是旧契约里的目标实体，这里不需要
 *
 * 取消时**resolve `undefined`**（与 `DialogUtil.show` 一致）：调用方必须判空，
 * 否则表现为「点了取消，页面却拿到了个 undefined 去取属性」。
 */
const props = defineProps<IDialogActionProps<IUploadConfig>>()

/** 兼容旧契约的 `param.data.category` */
const option = computed<IUploadOption>(() => {
  const param = props.param ?? {}
  return {
    category: param.category ?? param.data?.category,
    accept: param.accept,
    maxSizeMB: param.maxSizeMB,
  }
})

const multiple = computed(() => props.param?.multiple ?? props.isMultiple ?? false)
const title = computed(() => props.param?.title || '上传文件')
const files = ref<IUploadedFile[]>([])

/** 没选文件时不给确认（与原库「未选择文件不能确定」一致） */
const disableConfirm = computed(() => files.value.length === 0)

function onConfirm(): void {
  const list = files.value
  const payload = multiple.value ? list : list[0]
  props.onConfirm?.(payload)
}
</script>

<template>
  <KDialog
    :title="title"
    width="520px"
    :disable-confirm="disableConfirm"
    @confirm="onConfirm"
    @cancel="props.onCancel?.()"
  >
    <ElAlert
      v-if="multiple"
      class="k-upload-dialog__hint"
      type="info"
      :closable="false"
      show-icon
      title="可一次选择多个文件，全部上传完成后点确定"
    />
    <KUpload
      v-model="files"
      :category="option.category"
      :accept="option.accept"
      :max-size-m-b="option.maxSizeMB"
      :multiple="multiple"
      :tip="props.param?.tip || ''"
    />
  </KDialog>
</template>

<style scoped>
.k-upload-dialog__hint {
  margin-bottom: 12px;
}
</style>
