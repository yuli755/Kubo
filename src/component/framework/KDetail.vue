<script lang="ts" setup generic="E extends IIdentifiableEntity, S extends AbstractCurdService<E>">
import type { AbstractCurdService, IIdentifiableEntity, ITransformerConstructor } from '@/framework'
import { Printer } from '@element-plus/icons-vue'
import { ElButton, ElDescriptions, ElDescriptionsItem, ElEmpty } from 'element-plus'
import { computed } from 'vue'
import { getCellText, getDecoratedFieldList, getDictionary, getFieldLabel, getTableConfig } from '@/framework'
import { useDetail } from '@/hook/kubo/useDetail'

/**
 * # KDetail —— 只读详情（替代 airpower `useDetail` + `ADialog` + `AFormField` 的组合）
 */

const props = withDefaults(defineProps<{
  /** 服务类（`entityClass` 参与类型推断，见 `useTable` 注释） */
  service: new () => S & { entityClass: ITransformerConstructor<E> }
  /** 记录 id */
  id: number | null | undefined
  /** 标题（不传则只渲染字段区） */
  title?: string
  /** 每行几列 */
  column?: number
  /** 缓存键（缺省按模型名生成） */
  queryKey?: string[]
  /** 只显示这些字段（缺省显示全部被装饰过的字段） */
  fields?: string[]
}>(), {
  column: 2,
})

const detail = useDetail(props.service, {
  queryKey: props.queryKey ?? [`detail:${props.service.name}`],
  id: computed(() => props.id),
})

const EntityClass = computed(() => detail.entityClass as unknown as object)

/** 字段顺序：先按 `@Table` 的 order（表格列的顺序），再补上其余被装饰字段 */
const visibleFields = computed<string[]>(() => {
  if (props.fields?.length) {
    return props.fields
  }
  const decorated = getDecoratedFieldList(EntityClass.value)
  const tableOrder = [...decorated].sort((a, b) => (getTableConfig(EntityClass.value, b)?.order || 0) - (getTableConfig(EntityClass.value, a)?.order || 0))
  return tableOrder.filter(key => !key.startsWith('__'))
})

function fieldLabel(key: string): string {
  return getFieldLabel(EntityClass.value, key)
}

function fieldText(key: string): string {
  const entity = detail.entity.value
  if (!entity) {
    return '-'
  }
  const config = getTableConfig(EntityClass.value, key) ?? { key }
  return getCellText(entity, config, getDictionary(EntityClass.value, key))
}

function printDetail() {
  if (typeof window !== 'undefined') {
    window.print()
  }
}
</script>

<template>
  <div class="k-detail">
    <div
      v-if="title"
      class="k-detail__header"
    >
      <div class="k-detail__header-left">
        <div class="k-detail__bar" />
        <span class="k-detail__title">{{ title }}</span>
      </div>
      <ElButton
        class="k-detail__print-btn"
        link
        size="small"
        :icon="Printer"
        @click="printDetail"
      >
        打印单据
      </ElButton>
    </div>

    <ElEmpty
      v-if="!detail.isLoading.value && !detail.entity.value"
      description="没有找到该记录，可能已被删除"
      :image-size="80"
    />

    <ElDescriptions
      v-else
      v-loading="detail.isLoading.value"
      :column="column"
      border
      size="default"
      class="k-detail__descriptions"
    >
      <ElDescriptionsItem
        v-for="key in visibleFields"
        :key="key"
        :label="fieldLabel(key)"
        label-class-name="k-detail__label"
      >
        <slot
          :name="`field-${key}`"
          :entity="detail.entity.value"
          :value="(detail.entity.value as unknown as Record<string, unknown> | undefined)?.[key]"
        >
          {{ fieldText(key) }}
        </slot>
      </ElDescriptionsItem>
    </ElDescriptions>
  </div>
</template>

<style scoped>
.k-detail {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.k-detail__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.k-detail__header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.k-detail__bar {
  width: 3px;
  height: 14px;
  background: var(--el-color-primary);
  border-radius: 2px;
}

.k-detail__title {
  font-size: 15px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.k-detail :deep(.k-detail__label) {
  background-color: var(--el-fill-color-light) !important;
  font-weight: 500;
  color: var(--el-text-color-regular);
  width: 130px;
}

.k-detail :deep(.el-descriptions__content) {
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-primary);
}

@media print {
  .k-detail__print-btn {
    display: none !important;
  }
  .k-detail {
    width: 100% !important;
    padding: 0 !important;
  }
  .k-detail :deep(.el-descriptions) {
    page-break-inside: avoid;
    break-inside: avoid;
  }
  .k-detail :deep(.k-detail__label) {
    background-color: var(--kubo-print-label-bg) !important;
    -webkit-print-color-adjust: exact;
    print-color-adjust: exact;
    color: var(--kubo-print-label-text) !important;
  }
  .k-detail :deep(.el-descriptions__content) {
    color: var(--kubo-print-text) !important;
  }
}
</style>
