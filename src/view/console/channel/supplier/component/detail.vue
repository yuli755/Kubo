<script lang="ts" setup>
import { ElDescriptions, ElDescriptionsItem } from 'element-plus'
import { computed } from 'vue'
import KDialog from '@/component/framework/KDialog.vue'
import { getCellText, getFieldLabel, getTableConfig } from '@/framework'
import { KSupplierService } from '@/hook/channel/useSupplier'
import { useDetail } from '@/hook/kubo/useDetail'
import { SupplierEntity as Supplier } from '@/model/channel/supplier/SupplierEntity'

/**
 * # 供应商详情（F4 批 5 · 新底座）
 *
 * 只读弹窗，声明式（`:id` + `v-model`）——采购价列表点供应商名时打开。
 *
 * 旧版本是 airpower `ADialog + useDetail`，字段靠 `AFormField` 逐个手写；
 * 这里的主表信息按 `@KTable` 元数据渲染（`getCellText` 顺便统一了脱敏与电话格式），
 * 字段增减只改实体装饰器，不用回来改这个文件。
 *
 * 电话是后端 `@SensitiveField` 标记的字段：`getCellText` 会按 `desensitize` 元数据渲染，
 * 明文权限在数据权限策略里控制，页面不自己判断"该不该遮"。
 */
const props = defineProps<{
  modelValue: boolean
  /** 供应商 id */
  id: number | null | undefined
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
}>()

const detail = useDetail(KSupplierService, {
  queryKey: ['channel:supplier'],
  id: computed(() => props.id),
})

const supplier = computed(() => detail.entity.value as unknown as Record<string, unknown> | undefined)
const fields = ['code', 'name', 'phone'] as const

function fieldLabel(key: string): string {
  // 头部字段未必是列表列，必须查 @KField 的 getFieldLabel；
  // 查 getTableConfig 会拿不到配置而退回原始英文键名（N-11）。
  return getFieldLabel(Supplier, key)
}

function fieldText(key: string): string {
  const entity = supplier.value
  if (!entity) {
    return '-'
  }
  return getCellText(entity as never, { ...(getTableConfig(Supplier, key) ?? {}), key } as never)
}
</script>

<template>
  <KDialog
    :model-value="modelValue"
    confirm-text="关闭"
    hide-cancel
    min-height="240px"
    title="供应商详情"
    width="560px"
    @confirm="emit('update:modelValue', false)"
    @update:model-value="value => emit('update:modelValue', value)"
  >
    <ElDescriptions
      v-loading="detail.isLoading.value"
      :column="1"
      border
      size="small"
    >
      <ElDescriptionsItem
        v-for="field in fields"
        :key="field"
        :label="fieldLabel(field)"
      >
        {{ fieldText(field) }}
      </ElDescriptionsItem>
    </ElDescriptions>
  </KDialog>
</template>
