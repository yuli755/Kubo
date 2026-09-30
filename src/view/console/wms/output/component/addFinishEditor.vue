<script lang="ts" setup>
import type { InventoryEntity } from '@/model/wms/inventory/InventoryEntity'
import { ElButton, ElForm, ElFormItem, ElInput, ElInputNumber } from 'element-plus'
import { computed, ref, watch } from 'vue'
import KDialog from '@/component/framework/KDialog.vue'
import { FeedbackUtil, getFieldLabel } from '@/framework'
import { KOutputService } from '@/hook/wms/useOutput'
import { InventoryEntity as Inventory } from '@/model/wms/inventory/InventoryEntity'
import { InventoryTypeEnum } from '@/model/wms/inventory/InventoryTypeEnum'
import { OutputDetailEntity } from '@/model/wms/output/OutputDetailEntity'
import { InventorySelector } from '@/view/console/wms/inventory/component'

/**
 * # 确认出库（F4 批 4 · 新底座）
 *
 * 单据「作业」的第二种形态：出库要**先选一条具体库存**（从哪批货里扣），再填本次出库数量。
 *
 * ## 后端契约（E-H1 起，批 4 保持不变）
 * `POST /output/addFinish` 的载荷是明细，后端从载荷读：
 * - `quantity` —— **本次**数量（后端累加，传累计值会把明细数量翻倍）；
 * - `inventory.id` —— 扣减来源（缺它会失败）。
 *
 * ## 与旧版的差别
 * - 库存选择器 `InventorySelector` 已在本批迁到新底座（`IDialogActionProps` 契约不变），
 *   这里**声明式**打开它，不再需要全局 `DialogUtil` 上下文；
 * - 选中库存后数量默认 `min(该库存可用量, 待出数量)` —— 与旧版一致：
 *   默认值只求"点一次就能提交"，超量仍由后端拒绝。
 */
const props = defineProps<{
  modelValue: boolean
  /** 待出库的明细 */
  detail: OutputDetailEntity | null
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'confirmed'): void
}>()

const quantity = ref(0)
const inventory = ref<InventoryEntity | undefined>(undefined)
const inventoryPicking = ref(false)
const inventorySeed = ref<InventoryEntity | undefined>(undefined)
const submitting = ref(false)

const waitingQuantity = computed(() =>
  Math.max(0, Number(props.detail?.quantity ?? 0) - Number(props.detail?.finishQuantity ?? 0)),
)

watch(
  () => [props.modelValue, props.detail] as const,
  ([opened, detail]) => {
    if (opened && detail) {
      quantity.value = Math.max(0, Number(detail.quantity ?? 0) - Number(detail.finishQuantity ?? 0))
      // 明细上已带库存（后端回填过）则直接沿用，否则让用户选
      inventory.value = detail.inventory?.id ? detail.inventory : undefined
    }
  },
  { immediate: true },
)

const materialName = computed(() => props.detail?.material?.name ?? '-')
const unitName = computed(() => props.detail?.material?.unit?.name ?? '')
const inventoryQuantity = computed(() => inventory.value?.quantity ?? 0)

const inventoryStorageLabel = computed(() => {
  const s = inventory.value?.storage as { name?: string, code?: string } | undefined
  if (!s?.name) {
    return ''
  }
  return s.code ? `${s.name}(${s.code})` : s.name
})

const maxAvailableQuantity = computed(() => {
  if (!inventory.value) {
    return waitingQuantity.value
  }
  return Math.min(Number(inventory.value.quantity ?? 0), waitingQuantity.value)
})

function openInventoryPicker() {
  // 按仓库维度挑库存，并带上物料：选择器会按物料过滤（见 inventory selector 的文件头说明）
  const seed = new Inventory()
  seed.type = InventoryTypeEnum.STORAGE.key
  if (props.detail?.material) {
    seed.material = props.detail.material.copy()
  }
  inventorySeed.value = seed
  inventoryPicking.value = true
}

function onInventoryPicked(picked: unknown) {
  const row = picked as InventoryEntity | undefined
  inventoryPicking.value = false
  if (!row) {
    return
  }
  inventory.value = row
  // 默认值取「库存可用量」与「待出数量」的较小者：超量提交只会被后端拒绝
  quantity.value = Math.min(Number(row.quantity ?? 0), waitingQuantity.value)
}

async function onConfirm() {
  const detail = props.detail
  const source = inventory.value
  if (!detail?.id) {
    return
  }
  if (!source?.id) {
    FeedbackUtil.toastWarning('请选择库存来源')
    return
  }
  submitting.value = true
  try {
    await new KOutputService().addDetailFinishQuantity({
      id: detail.id,
      quantity: quantity.value,
      inventory: { id: source.id },
    } as never)
    FeedbackUtil.toastSuccess('明细出库成功')
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
    :disable-confirm="quantity <= 0"
    :model-value="modelValue"
    confirm-text="确认出库"
    title="确认出库"
    width="600px"
    @cancel="emit('update:modelValue', false)"
    @confirm="onConfirm"
    @update:model-value="value => emit('update:modelValue', value)"
  >
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
      <ElFormItem label="库存来源">
        <div class="field-select-row">
          <ElInput
            :model-value="inventoryStorageLabel"
            placeholder="请选择库存来源..."
            readonly
            @click="openInventoryPicker"
          />
          <ElButton @click="openInventoryPicker">
            选择
          </ElButton>
        </div>
      </ElFormItem>
      <ElFormItem
        v-if="inventory"
        label="当前库存"
      >
        <ElInput
          :model-value="String(inventoryQuantity)"
          disabled
        >
          <template #append>
            {{ unitName }}
          </template>
        </ElInput>
      </ElFormItem>
      <ElFormItem label="待出数量">
        <ElInput
          :model-value="String(waitingQuantity)"
          disabled
        >
          <template #append>
            {{ unitName }}
          </template>
        </ElInput>
      </ElFormItem>
      <ElFormItem :label="getFieldLabel(OutputDetailEntity, 'quantity')">
        <div class="quantity-input-row">
          <ElInputNumber
            v-model="quantity"
            :min="0"
            class="add-finish-editor__number"
          />
          <span class="add-finish-editor__unit">{{ unitName }}</span>
          <ElButton
            v-if="maxAvailableQuantity > 0"
            size="small"
            type="primary"
            link
            @click="quantity = maxAvailableQuantity"
          >
            全部出库（{{ maxAvailableQuantity }}）
          </ElButton>
        </div>
      </ElFormItem>
    </ElForm>
  </KDialog>

  <!-- ⚠️ 这里必须写 @confirm / @cancel，不能写 :on-confirm / :on-cancel。
       selector.vue 的 pick() 是 `emit('confirm', row)`，而 Vue 的 emit 只按
       vnode.props.onConfirm（@confirm 编译出的键）查处理器；`:on-confirm` 编译成
       连字符键 "on-confirm"，只会被塞进已声明的 props，emit 永远找不到它 ——
       选中的库存会被静默丢弃，选择器也关不掉（visible 依赖宿主回写 modelValue）。
       与 KDialog.vue 头注释「外层必须写 @confirm / @cancel」同一条铁律。 -->
  <InventorySelector
    v-if="inventoryPicking"
    :param="inventorySeed"
    @cancel="inventoryPicking = false"
    @confirm="onInventoryPicked"
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
