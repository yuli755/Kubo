<script lang="ts" setup>
import type { FormInstance } from 'element-plus'
import type { StorageEntity } from '@/model/factory/storage/StorageEntity'
import { ElButton, ElForm, ElFormItem, ElInput, ElInputNumber } from 'element-plus'
import { computed, ref, watch } from 'vue'
import KDialog from '@/component/framework/KDialog.vue'
import KSelector from '@/component/framework/KSelector.vue'
import { getFieldLabel, validateFormWithFeedback, WebValidator } from '@/framework'
import { KMaterialService } from '@/hook/asset/useMaterial'
import { KStorageService } from '@/hook/factory/useStorage'
import { InputDetailEntity } from '@/model/wms/input/InputDetailEntity'

/**
 * # 入库明细编辑（F4 批 4 · 新底座）
 *
 * 编辑一条入库明细：选物料 + 选目标仓库 + 填数量（数量后面跟着物料的计量单位）。
 *
 * 与旧版的差别：
 * - 物料与仓库选择采用 `KSelector`（物料走普通列表，仓库走 `tree` 树形模式）；
 * - 字段能选择绝不让用户手工输入：物料和目标仓库均提供可视化选择弹窗；
 * - 校验规则由 `WebValidator.createRules(InputDetailEntity)` 从 `@KForm` 元数据生成；
 * - 选中物料/仓库时一并回填关联 ID 与展示名称。
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
const storagePicking = ref(false)

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

const storageName = computed(() => {
  if (form.value?.storage) {
    const s = form.value.storage as { name?: string, code?: string }
    return s.name ? (s.code ? `${s.name}(${s.code})` : s.name) : ''
  }
  return ''
})

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

function onStoragePicked(rows: unknown[]) {
  const target = rows[0] as StorageEntity | undefined
  storagePicking.value = false
  if (!form.value || !target) {
    return
  }
  form.value.storage = target
  form.value.storageId = target.id
}

const formRef = ref<FormInstance>()

/**
 * R52 反馈修复：原来点「确定」直接关窗交数据，`rules` 生成了却没人调用 validate() ——
 * 物料/仓库/数量没填也被静默收进上一层，用户完全不知道哪里没填。现在先走框架层的
 * 统一校验反馈（标红 + 滚动定位 + toast 点名第一个出错字段），通过才关窗回传。
 * 配套：KDialog 传 `close-on-confirm=false` —— 异步校验还没出结果时不能先把窗关掉。
 */
async function onConfirm() {
  if (!form.value) {
    return
  }
  if (!await validateFormWithFeedback(formRef.value, InputDetailEntity)) {
    return
  }
  emit('confirm', form.value)
  emit('update:modelValue', false)
}
</script>

<template>
  <KDialog
    :model-value="modelValue"
    :close-on-confirm="false"
    confirm-text="确定"
    title="入库明细"
    width="600px"
    @cancel="emit('update:modelValue', false)"
    @confirm="onConfirm"
    @update:model-value="value => emit('update:modelValue', value)"
  >
    <ElForm
      v-if="form"
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="110px"
      @submit.prevent
    >
      <ElFormItem
        label="入库物料"
        prop="material"
      >
        <div class="field-select-row">
          <ElInput
            :model-value="form.material?.name ?? ''"
            placeholder="请选择入库物料..."
            readonly
            @click="materialPicking = true"
          />
          <ElButton @click="materialPicking = true">
            选择
          </ElButton>
        </div>
      </ElFormItem>

      <ElFormItem
        label="目标仓库"
        prop="storage"
      >
        <div class="field-select-row">
          <ElInput
            :model-value="storageName"
            placeholder="请选择目标仓库..."
            readonly
            @click="storagePicking = true"
          />
          <ElButton @click="storagePicking = true">
            选择
          </ElButton>
        </div>
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

  <KSelector
    v-model="storagePicking"
    :selected="form?.storage ? [form.storage] : []"
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

.input-detail-editor__number {
  width: 180px;
}

.input-detail-editor__unit {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
}
</style>
