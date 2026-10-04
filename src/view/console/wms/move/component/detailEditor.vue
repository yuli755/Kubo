<script lang="ts" setup>
import type { FormInstance } from 'element-plus'
import { ElButton, ElForm, ElFormItem, ElInput, ElInputNumber } from 'element-plus'
import { computed, ref, watch } from 'vue'
import KDialog from '@/component/framework/KDialog.vue'
import { getFieldLabel, validateFormWithFeedback, WebValidator } from '@/framework'
import { MoveDetailEntity } from '@/model/wms/move/MoveDetailEntity'

/**
 * # 移库明细编辑（F4 批 4 · 新底座）
 *
 * 编辑一条移库明细：只读显示「转移物料」（来自上一步选的库存），可改「移动数量」，
 * 数量后面跟着物料的计量单位。
 *
 * 与旧版的差别：
 * - 旧版是 `DialogProps.withParam(detail)` 命令式打开（宿主 `await DialogUtil.show(...)` 拿返回值）；
 *   现在是**声明式**：`v-model` 控显隐 + `@confirm` 交出编辑后的明细 ——
 *   调用方（移库编辑器）不用再依赖全局的 `DialogUtil` 上下文，测试里也能直接挂载。
 * - 校验规则由 `WebValidator.createRules(MoveDetailEntity)` 从 `@KForm` 元数据生成，
 *   不再调旧服务上的 `createValidator()`（那份读的是 airpower 注册表）。
 *
 * 数量默认「该库存的可用数量」：手工填一个比库存还大的数量会被后端拒绝，
 * 默认值贴近上限是为了少一次试错（上限的最终判定仍在后端）。
 */
const props = defineProps<{
  modelValue: boolean
  /** 待编辑的明细（新增流程里是一份新建的副本） */
  detail: MoveDetailEntity | null
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'confirm', detail: MoveDetailEntity): void
}>()

const form = ref<MoveDetailEntity | null>(null)

watch(
  () => [props.modelValue, props.detail] as const,
  ([opened, detail]) => {
    if (opened && detail) {
      // 复制一份再编辑：取消时不会把半截修改留在上一层的数组里
      form.value = detail.copy() as MoveDetailEntity
    }
  },
  { immediate: true },
)

const rules = computed(() => WebValidator.createRules(MoveDetailEntity as never) as unknown as Record<string, unknown[]>)

const availableQuantity = computed(() => Number(form.value?.inventory?.quantity) || 0)
const unitName = computed(() => form.value?.inventory?.material?.unit?.name ?? '')
const materialName = computed(() => form.value?.inventory?.material?.name ?? '-')

const formRef = ref<FormInstance>()

/**
 * R52 反馈修复：数量清空/为 0 时原来照样「确定」—— 规则生成了却没人 validate()，
 * 半截明细被静默收进明细表。现在先走框架层统一校验反馈，通过才关窗回传。
 * 配套：KDialog 传 `close-on-confirm=false`（异步校验没出结果前不能先关窗），
 * 通过后自己 `emit('update:modelValue', false)` 收口。
 */
async function onConfirm() {
  if (!form.value) {
    return
  }
  if (!await validateFormWithFeedback(formRef.value, MoveDetailEntity)) {
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
    title="移库明细"
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
      <ElFormItem label="转移物料">
        <ElInput
          :model-value="materialName"
          disabled
        />
      </ElFormItem>
      <ElFormItem
        :label="getFieldLabel(MoveDetailEntity, 'quantity')"
        prop="quantity"
      >
        <div class="field-quick-row">
          <ElInputNumber
            v-model="form.quantity as number"
            :min="0"
            :max="availableQuantity > 0 ? availableQuantity : undefined"
            class="detail-editor__number"
          />
          <span class="detail-editor__unit">{{ unitName }}</span>
          <ElButton
            v-if="availableQuantity > 0"
            size="small"
            type="primary"
            plain
            @click="form.quantity = availableQuantity"
          >
            全部转移（{{ availableQuantity }} {{ unitName }}）
          </ElButton>
        </div>
      </ElFormItem>
    </ElForm>
  </KDialog>
</template>

<style lang="scss" scoped>
.field-quick-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.detail-editor__number {
  width: 180px;
}

.detail-editor__unit {
  margin-left: 2px;
  color: var(--el-text-color-secondary);
}
</style>
