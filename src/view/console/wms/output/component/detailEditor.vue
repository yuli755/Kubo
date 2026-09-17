<script lang="ts" setup>
import { ElForm, ElFormItem, ElInput, ElInputNumber } from 'element-plus'
import { computed, ref, watch } from 'vue'
import KDialog from '@/component/framework/KDialog.vue'
import KSelector from '@/component/framework/KSelector.vue'
import { getFieldLabel, WebValidator } from '@/framework'
import { KMaterialService } from '@/hook/asset/useMaterial'
import { OutputDetailEntity } from '@/model/wms/output/OutputDetailEntity'

/**
 * # 出库明细编辑（F4 批 4 · 新底座）
 *
 * 与入库明细编辑器同构：选物料 + 填数量（数量后缀是物料单位）。
 * 差别只有一处**业务含义**：出库的「来源」不在这一步定 —— 出库时从具体库存里扣
 * （见 `OutputAddFinishEditor` 的库存选择），所以这里不选仓库。
 */
const props = defineProps<{
  modelValue: boolean
  detail: OutputDetailEntity | null
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'confirm', detail: OutputDetailEntity): void
}>()

const form = ref<OutputDetailEntity | null>(null)
const materialPicking = ref(false)

watch(
  () => [props.modelValue, props.detail] as const,
  ([opened, detail]) => {
    if (opened && detail) {
      form.value = detail.copy() as OutputDetailEntity
    }
  },
  { immediate: true },
)

const rules = computed(() => WebValidator.createRules(OutputDetailEntity as never) as unknown as Record<string, unknown[]>)

const unitName = computed(() => form.value?.material?.unit?.name ?? '')

function onMaterialPicked(rows: unknown[]) {
  const material = rows[0] as OutputDetailEntity['material'] | undefined
  materialPicking.value = false
  if (!form.value || !material) {
    return
  }
  form.value.material = material
  form.value.materialId = material.id
  form.value.materialName = material.name
}

function onConfirm() {
  if (form.value) {
    emit('confirm', form.value)
  }
  emit('update:modelValue', false)
}
</script>

<template>
  <KDialog
    :model-value="modelValue"
    confirm-text="确定"
    title="出库明细"
    width="600px"
    @cancel="emit('update:modelValue', false)"
    @confirm="onConfirm"
    @update:model-value="value => emit('update:modelValue', value)"
  >
    <ElForm
      v-if="form"
      :model="form"
      :rules="rules"
      label-width="110px"
      @submit.prevent
    >
      <ElFormItem
        label="出库物料"
        prop="material"
      >
        <ElInput
          :model-value="form.material?.name ?? ''"
          placeholder="请选择出库物料..."
          readonly
          @click="materialPicking = true"
        />
      </ElFormItem>
      <ElFormItem
        :label="getFieldLabel(OutputDetailEntity, 'quantity')"
        prop="quantity"
      >
        <ElInputNumber
          v-model="form.quantity as number"
          :min="0"
          class="output-detail-editor__number"
        />
        <span class="output-detail-editor__unit">{{ unitName }}</span>
      </ElFormItem>
    </ElForm>
  </KDialog>

  <KSelector
    v-model="materialPicking"
    :selected="form?.material ? [form.material] : []"
    :service="KMaterialService"
    title="选择物料"
    @confirm="onMaterialPicked"
  />
</template>

<style lang="scss" scoped>
.output-detail-editor__number {
  width: 180px;
}

.output-detail-editor__unit {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
}
</style>
