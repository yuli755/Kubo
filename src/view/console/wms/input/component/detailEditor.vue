<script lang="ts" setup>
import { ElForm, ElFormItem, ElInput, ElInputNumber } from 'element-plus'
import { computed, ref, watch } from 'vue'
import KDialog from '@/component/framework/KDialog.vue'
import KSelector from '@/component/framework/KSelector.vue'
import { getFieldLabel, WebValidator } from '@/framework'
import { KMaterialService } from '@/hook/asset/useMaterial'
import { InputDetailEntity } from '@/model/wms/input/InputDetailEntity'

/**
 * # 入库明细编辑（F4 批 4 · 新底座）
 *
 * 编辑一条入库明细：选物料 + 填数量（数量后面跟着物料的计量单位）。
 *
 * 与旧版的差别：
 * - 物料选择从 airpower `ASelect` + `MaterialSelector` 换成 `KSelector` + `KMaterialService`
 *   —— 选择器不再需要「页面级弹窗参数契约」，就是 `v-model` + `@confirm` 两个口子；
 * - 校验规则由 `WebValidator.createRules(InputDetailEntity)` 从 `@KForm` 元数据生成
 *   （旧版调 `InputDetailService.createValidator()`，读的是 airpower 注册表）。
 *
 * 选中物料时会一并回填 `materialId` / `materialName`：后端按 `materialId` 关联物料，
 * 而明细表格列（`materialCode`/`materialName`）走的是嵌套对象 —— 两处都要有值，
 * 否则「刚加进明细的行」要么提交不上、要么在表格里显示为空。
 */
const props = defineProps<{
  modelValue: boolean
  /** 待编辑的明细（新增流程里是一份新建的副本） */
  detail: InputDetailEntity | null
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'confirm', detail: InputDetailEntity): void
}>()

const form = ref<InputDetailEntity | null>(null)
const materialPicking = ref(false)

watch(
  () => [props.modelValue, props.detail] as const,
  ([opened, detail]) => {
    if (opened && detail) {
      // 复制一份再编辑：取消时不会把半截修改留在上一层的数组里
      form.value = detail.copy() as InputDetailEntity
    }
  },
  { immediate: true },
)

const rules = computed(() => WebValidator.createRules(InputDetailEntity as never) as unknown as Record<string, unknown[]>)

const unitName = computed(() => form.value?.material?.unit?.name ?? '')

function onMaterialPicked(rows: unknown[]) {
  const material = rows[0] as InputDetailEntity['material'] | undefined
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
    title="入库明细"
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
        label="入库物料"
        prop="material"
      >
        <ElInput
          :model-value="form.material?.name ?? ''"
          placeholder="请选择入库物料..."
          readonly
          @click="materialPicking = true"
        />
      </ElFormItem>
      <ElFormItem
        :label="getFieldLabel(InputDetailEntity, 'quantity')"
        prop="quantity"
      >
        <ElInputNumber
          v-model="form.quantity as number"
          :min="0"
          class="input-detail-editor__number"
        />
        <span class="input-detail-editor__unit">{{ unitName }}</span>
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
.input-detail-editor__number {
  width: 180px;
}

.input-detail-editor__unit {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
}
</style>
