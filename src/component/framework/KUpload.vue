<script lang="ts" setup>
import type { IUploadedFile, IUploadOption } from '@/framework'
import { Delete, Document, UploadFilled } from '@element-plus/icons-vue'
import { ElButton, ElIcon, ElImage, ElProgress } from 'element-plus'
import { computed, ref } from 'vue'
import { FeedbackUtil, UploadUtil } from '@/framework'

/**
 * # KUpload —— 文件上传器（替代 airpower `AFormField showUpload` / `AImage` / `showUpload` 弹窗的内容体）
 *
 * ## 为什么自己做
 * 原库把上传拆成三处：`ADialog + AFormField(showUpload)`（表单里的字段）、`AImage`（头像/图片）、
 * `DialogUtil.showUpload()`（弹窗上传）。三处各有一套 props 与回调，页面要按场景挑一个。
 * 这里只做**一个组件**：
 *
 * - 表单里直接用：`<KUpload v-model="form.files" :category="FileCategory.CONTRACT_ATTACHMENT.key" />`
 * - 命令式弹窗：`DialogUtil.showUpload({ category, accept })` —— 内部就是用这个组件（见 `KUploadDialog.vue`）
 *
 * ## 契约
 * - `v-model` 绑定**已上传文件数组**（`IUploadedFile[]`），只读展示时传 `readonly`
 * - 上传进度、类型/大小校验都在 `UploadUtil` 里（纯函数，可单测），组件只负责交互与展示
 * - 组件自身**不发 toast 之外的业务判断**：校验失败给提示、成功把新记录并进 `v-model`
 *
 * ## 为什么不用 `ElUpload` 的 action / auto-upload
 * `ElUpload` 默认自己发请求（要 `action` URL、要处理它的响应格式），而本仓的上传要走
 * `src/api/axios.ts` 的统一实例（鉴权头、租户头、演示模式 mock 都挂在它上面）。
 * 因此这里用**原生 input + 拖拽区**自己收集文件、交给 `UploadUtil` 上传 —— 少一层
 * 「ElUpload 的请求配置」和「我们的请求配置」的映射，也不需要 `accept` 之外的魔法。
 */

const props = withDefaults(defineProps<IUploadOption & {
  /** 已上传的文件列表 */
  modelValue?: IUploadedFile[]
  /** 是否多选（单选时新文件会替换旧文件） */
  multiple?: boolean
  /** 提示文案（不传按 accept/大小自动生成） */
  tip?: string
  /** 只读（详情页展示；隐藏选择与删除） */
  readonly?: boolean
  /** 紧凑模式（表单里用，高度更小） */
  compact?: boolean
}>(), {
  modelValue: () => [],
  multiple: false,
  tip: '',
  readonly: false,
  compact: false,
})

const emit = defineEmits<{
  'update:modelValue': [files: IUploadedFile[]]
  /** 单个文件上传成功 */
  'uploaded': [file: IUploadedFile]
  /** 校验或上传失败（原因已 toast，页面可按需处理） */
  'failed': [reason: string]
}>()

const inputRef = ref<HTMLInputElement>()
const uploading = ref(false)
const percent = ref(0)
const dragOver = ref(false)

const files = computed(() => props.modelValue ?? [])
const isFull = computed(() => !props.multiple && files.value.length > 0)
const tipText = computed(() => {
  if (props.tip) {
    return props.tip
  }
  const parts: string[] = []
  if (props.accept) {
    parts.push(`支持 ${props.accept}`)
  }
  const maxSize = props.maxSizeMB ?? UploadUtil.defaultMaxSizeMB
  if (maxSize > 0) {
    parts.push(`不超过 ${maxSize} MB`)
  }
  if (props.multiple) {
    parts.push('可多选')
  }
  return parts.join(' · ')
})

function openPicker(): void {
  if (props.readonly || uploading.value) {
    return
  }
  inputRef.value?.click()
}

/** 原生 input 的 change：**取完就把 input 清空**，否则同一个文件第二次选不会触发 change */
async function onPicked(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const picked = Array.from(input.files ?? [])
  input.value = ''
  await handleFiles(picked)
}

async function onDrop(event: DragEvent): Promise<void> {
  dragOver.value = false
  if (props.readonly) {
    return
  }
  const dropped = Array.from(event.dataTransfer?.files ?? [])
  await handleFiles(dropped)
}

async function handleFiles(picked: File[]): Promise<void> {
  if (picked.length === 0) {
    return
  }
  const limit = props.multiple ? picked : picked.slice(0, 1)
  uploading.value = true
  percent.value = 0
  try {
    const uploaded: IUploadedFile[] = []
    for (const file of limit) {
      const reason = UploadUtil.validate(file, props)
      if (reason) {
        FeedbackUtil.toastError(reason)
        emit('failed', reason)
        continue
      }
      const record = await UploadUtil.upload(file, {
        ...props,
        onProgress: value => (percent.value = value),
      })
      uploaded.push(record)
      emit('uploaded', record)
    }
    if (uploaded.length > 0) {
      emit('update:modelValue', props.multiple ? [...files.value, ...uploaded] : [uploaded[uploaded.length - 1] as IUploadedFile])
    }
  }
  catch (error) {
    const reason = error instanceof Error ? error.message : '上传失败'
    FeedbackUtil.toastError(reason)
    emit('failed', reason)
  }
  finally {
    uploading.value = false
    percent.value = 0
  }
}

function remove(index: number): void {
  if (props.readonly) {
    return
  }
  const next = [...files.value]
  next.splice(index, 1)
  emit('update:modelValue', next)
}
</script>

<template>
  <div class="k-upload">
    <template v-if="!readonly">
      <div
        v-if="!isFull"
        class="k-upload__picker"
        :class="{ 'is-compact': compact, 'is-over': dragOver }"
        @click="openPicker"
        @dragover.prevent="dragOver = true"
        @dragleave.prevent="dragOver = false"
        @drop.prevent="onDrop"
      >
        <ElIcon class="k-upload__icon">
          <UploadFilled />
        </ElIcon>
        <div class="k-upload__text">
          <span class="k-upload__action">点击选择</span>
          <span class="k-upload__sub">或把文件拖进来</span>
        </div>
      </div>
      <div
        v-if="tipText"
        class="k-upload__tip"
      >
        {{ tipText }}
      </div>
      <input
        ref="inputRef"
        class="k-upload__input"
        type="file"
        :accept="accept"
        :multiple="multiple"
        @change="onPicked"
      >
      <ElProgress
        v-if="uploading"
        class="k-upload__progress"
        :percentage="percent"
        :stroke-width="6"
      />
    </template>

    <ul
      v-if="files.length > 0"
      class="k-upload__list"
    >
      <li
        v-for="(file, index) in files"
        :key="file.id ?? `${file.name}-${index}`"
        class="k-upload__item"
      >
        <ElImage
          v-if="UploadUtil.isImage(file)"
          class="k-upload__thumb"
          :src="UploadUtil.resolveUrl(file.url)"
          fit="cover"
          :preview-src-list="[UploadUtil.resolveUrl(file.url)]"
          preview-teleported
        />
        <ElIcon
          v-else
          class="k-upload__thumb k-upload__thumb--file"
        >
          <Document />
        </ElIcon>
        <div class="k-upload__meta">
          <span class="k-upload__name">{{ file.name }}</span>
          <span class="k-upload__size">{{ UploadUtil.sizeText(file.size) }}</span>
        </div>
        <ElButton
          v-if="!readonly"
          class="k-upload__remove"
          link
          type="danger"
          :icon="Delete"
          @click="remove(index)"
        />
      </li>
    </ul>
    <div
      v-else-if="readonly"
      class="k-upload__empty"
    >
      暂无文件
    </div>
  </div>
</template>

<style scoped>
.k-upload__picker {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 18px 16px;
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
  background: var(--el-fill-color-blank);
  cursor: pointer;
  transition: border-color var(--kubo-duration-base), background-color var(--kubo-duration-base);
}

.k-upload__picker.is-compact {
  padding: 12px;
}

.k-upload__picker:hover,
.k-upload__picker.is-over {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.k-upload__icon {
  font-size: 22px;
  color: var(--el-text-color-secondary);
}

.k-upload__text {
  display: flex;
  flex-direction: column;
  line-height: 1.4;
}

.k-upload__action {
  font-size: 13px;
  color: var(--el-color-primary);
}

.k-upload__sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.k-upload__tip {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.k-upload__input {
  display: none;
}

.k-upload__progress {
  margin-top: 8px;
}

.k-upload__list {
  margin: 10px 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.k-upload__item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.k-upload__thumb {
  width: 40px;
  height: 40px;
  border-radius: 4px;
  flex: none;
}

.k-upload__thumb--file {
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--el-fill-color-light);
  font-size: 20px;
  color: var(--el-text-color-secondary);
}

.k-upload__meta {
  display: flex;
  flex-direction: column;
  min-width: 0;
  line-height: 1.4;
}

.k-upload__name {
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.k-upload__size {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.k-upload__remove {
  margin-left: auto;
}

.k-upload__empty {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>
