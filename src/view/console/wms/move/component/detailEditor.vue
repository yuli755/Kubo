<script lang="ts" setup>
import { ElForm, ElFormItem, ElInput, ElInputNumber } from 'element-plus'
import { computed, ref, watch } from 'vue'
import KDialog from '@/component/framework/KDialog.vue'
import { getFieldLabel, WebValidator } from '@/framework'
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

const unitName = computed(() => form.value?.inventory?.material?.unit?.name ?? '')
const materialName = computed(() => form.value?.inventory?.material?.name ?? '-')

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
    title="移库明细"
    width="600px"
    @cancel="emit('update:modelValue', false)"
    @confirm="onConfirm"
  >
    <ElForm
      v-if="form"
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
        <ElInputNumber
          v-model="form.quantity as number"
          :min="0"
          class="detail-editor__number"
        />
        <span class="detail-editor__unit">{{ unitName }}</span>
      </ElFormItem>
    </ElForm>
  </KDialog>
</template>

<style lang="scss" scoped>
.detail-editor__number {
  width: 180px;
}

.detail-editor__unit {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
}
</style>
