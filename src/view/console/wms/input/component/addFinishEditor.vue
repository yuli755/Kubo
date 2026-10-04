<script lang="ts" setup>
import type { StorageEntity } from '@/model/factory/storage/StorageEntity'
import { ElButton, ElForm, ElFormItem, ElInput, ElInputNumber } from 'element-plus'
import { computed, ref, watch } from 'vue'
import KDialog from '@/component/framework/KDialog.vue'
import KSelector from '@/component/framework/KSelector.vue'
import { FeedbackUtil, getFieldLabel } from '@/framework'
import { KStorageService } from '@/hook/factory/useStorage'
import { KInputService } from '@/hook/wms/useInput'
import { InputDetailEntity } from '@/model/wms/input/InputDetailEntity'

/**
 * # 确认入库（F4 批 4 · 新底座）
 *
 * 单据「作业」的第一种形态：给一条入库明细登记**本次入库数量**。
 *
 * ## 后端契约（E-H1 起，批 4 保持不变）
 * `POST /input/addFinish` 的载荷是**明细**，且后端从载荷里读两样东西：
 * - `quantity` —— **本次**数量（后端做累加，传累计值会把明细数量翻倍）；
 * - `storage.id` —— 入库目标仓库（缺它会 403「请传入入库仓库」）。
 *
 * 所以这里默认填「待入数量」（总量 − 已完成），并要求先选仓库再提交。
 *
 * ## 与旧版的差别
 * - 旧版用 `DialogUtil.show(InputAddFinishEditor, detail)`（命令式 + airpower 应用上下文），
 *   且往组件里塞了一个 `quantity` 本地变量来控制显示 —— 现在 `modelValue`/`detail` 两个 prop 说清一切；
 * - 旧版把「目标仓库」做成空壳 `AInput` + `DialogUtil.show(StorageSelector)`；现在用 `KSelector`
 *   （`tree` 模式，仓库是树形实体，选子仓库时能看到层级）—— `StorageSelector`（airpower）随之无消费者，已删。
 *
 * ## 一个**修掉的旧缺陷**
 * 旧版 `quantity` 的 ref 初值是 `detail.quantity - detail.finishQuantity`，但 `formData.quantity`
 * 被赋成这个值之后又被 `expose('id','quantity')` 裁剪，用户改数量前若直接确认，提交的其实是
 * 「待入数量」而不是 0 —— 看起来对，但一旦用户手动改过又清空，输入框会静默变成 0 并仍然提交。
 * 现在 `quantity` 是显式校验的（`<= 0` 时确认按钮禁用），空值不会被当成合法输入提交。
 */
const props = defineProps<{
  modelValue: boolean
  /** 待入库的明细 */
  detail: InputDetailEntity | null
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'confirmed'): void
}>()

const quantity = ref(0)
const storage = ref<StorageEntity | undefined>(undefined)
const storagePicking = ref(false)
const submitting = ref(false)

watch(
  () => [props.modelValue, props.detail] as const,
  ([opened, detail]) => {
    if (opened && detail) {
      // 默认「待入数量」：部分入库后再次确认时，默认值若还是总量会直接被后端拒绝
      quantity.value = Math.max(0, Number(detail.quantity ?? 0) - Number(detail.finishQuantity ?? 0))
      // 明细上已指定的仓库（如采购入库单的明细带了 storage）优先，没有则要求用户选
      storage.value = detail.storage?.id ? detail.storage : undefined
    }
  },
  { immediate: true },
)

const materialName = computed(() => props.detail?.material?.name ?? '-')
const unitName = computed(() => props.detail?.material?.unit?.name ?? '')
const waitingQuantity = computed(() =>
  Math.max(0, Number(props.detail?.quantity ?? 0) - Number(props.detail?.finishQuantity ?? 0)),
)

const storageLabel = computed(() => {
  if (!storage.value?.name) {
    return ''
  }
  return storage.value.code ? `${storage.value.name}(${storage.value.code})` : storage.value.name
})

function onStoragePicked(rows: unknown[]) {
  storage.value = rows[0] as StorageEntity | undefined
  storagePicking.value = false
}

async function onConfirm() {
  const detail = props.detail
  const target = storage.value
  if (!detail?.id) {
    return
  }
  if (!target?.id) {
    FeedbackUtil.toastWarning('请选择目标仓库')
    return
  }
  submitting.value = true
  try {
    await new KInputService().addDetailFinishQuantity({
      id: detail.id,
      quantity: quantity.value,
      storage: { id: target.id },
    } as never)
    FeedbackUtil.toastSuccess('明细入库成功')
    // 顺序不可换：先 confirmed 再关窗。KDialog 已配 :close-on-confirm="false"，
    // 此刻组件仍在树上，emit 才能送达详情层（否则 isUnmounted 守卫静默吞掉）
    emit('confirmed')
    emit('update:modelValue', false)
  }
  finally {
    submitting.value = false
  }
}
</script>

<template>
  <KDialog
    :close-on-confirm="false"
    :disable-confirm="quantity <= 0"
    :model-value="modelValue"
    confirm-text="确认入库"
    title="确认入库"
    width="600px"
    @cancel="emit('update:modelValue', false)"
    @confirm="onConfirm"
    @update:model-value="value => emit('update:modelValue', value)"
  >
    <!-- 提交中只在内容上盖 loading：`KDialog` 没有 loading 属性（它只管显隐/按钮），
         提交态属于内容自己的事 -->
    <ElForm
      v-loading="submitting"
      label-width="110px"
      @submit.prevent
    >
      <ElFormItem label="物料信息">
        <ElInput
          :model-value="materialName"
          disabled
        />
      </ElFormItem>
      <ElFormItem label="目标仓库">
        <div class="field-select-row">
          <ElInput
            :model-value="storageLabel"
            placeholder="请选择目标仓库..."
            readonly
            @click="storagePicking = true"
          />
          <ElButton @click="storagePicking = true">
            选择
          </ElButton>
        </div>
      </ElFormItem>
      <ElFormItem label="待入数量">
        <ElInput
          :model-value="String(waitingQuantity)"
          disabled
        >
          <template #append>
            {{ unitName }}
          </template>
        </ElInput>
      </ElFormItem>
      <ElFormItem :label="getFieldLabel(InputDetailEntity, 'quantity')">
        <div class="quantity-input-row">
          <ElInputNumber
            v-model="quantity"
            :min="0"
            class="add-finish-editor__number"
          />
          <span class="add-finish-editor__unit">{{ unitName }}</span>
          <ElButton
            size="small"
            type="primary"
            link
            @click="quantity = waitingQuantity"
          >
            全部入库（{{ waitingQuantity }}）
          </ElButton>
        </div>
      </ElFormItem>
    </ElForm>
  </KDialog>

  <KSelector
    v-model="storagePicking"
    :selected="storage ? [storage] : []"
    :service="KStorageService"
    title="选择目标仓库"
    tree
    @confirm="onStoragePicked"
  />
</template>

<style lang="scss" scoped>
.field-select-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.quantity-input-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.add-finish-editor__number {
  width: 180px;
}

.add-finish-editor__unit {
  margin-left: 4px;
  margin-right: 4px;
  color: var(--el-text-color-secondary);
}
</style>
