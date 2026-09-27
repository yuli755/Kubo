<script lang="ts" setup>
import type { WebEnum } from '@/framework'
import type { UseTableReturn } from '@/hook/kubo/useTable'
import { ElRadioButton, ElRadioGroup } from 'element-plus'
import { computed, watch } from 'vue'

export interface IStatusOption {
  label: string
  value: number | null
  color?: string
}

const props = withDefaults(
  defineProps<{
    modelValue?: number | null
    options?: IStatusOption[]
    enumClass?: any
    table?: UseTableReturn<any, any>
    field?: string
    title?: string
  }>(),
  {
    modelValue: null,
    options: undefined,
    enumClass: undefined,
    field: 'status',
    title: '单据状态',
  },
)

const emit = defineEmits<{
  (e: 'update:modelValue', val: number | null): void
  (e: 'change', val: number | null): void
}>()

const computedOptions = computed<IStatusOption[]>(() => {
  if (props.options && props.options.length > 0) {
    return props.options
  }
  if (props.enumClass) {
    const list = props.enumClass.getList() as WebEnum[]
    return [
      { label: '全部', value: null },
      ...list.map(item => ({
        label: item.getLabel() === '审核中' ? '待审核' : item.getLabel(),
        value: Number(item.key),
        color: item.getColor(),
      })),
    ]
  }
  return [{ label: '全部', value: null }]
})

const activeValue = computed<string | number>({
  get() {
    if (props.table) {
      const val = props.table.filter.value[props.field]
      return val !== undefined && val !== null && val !== '' ? Number(val) : ''
    }
    return props.modelValue !== null && props.modelValue !== undefined ? props.modelValue : ''
  },
  set(rawVal: unknown) {
    const val = rawVal === '' || rawVal === null || rawVal === undefined ? null : Number(rawVal)
    emit('update:modelValue', val)
    emit('change', val)
    if (props.table) {
      const next = { ...props.table.filter.value }
      if (val === null || val === undefined) {
        delete next[props.field]
      }
      else {
        next[props.field] = val
      }
      props.table.search(next)
    }
  },
})

// 当外部 table.reset() 清空 filter 时，同步外层 modelValue
watch(
  () => props.table?.filter.value[props.field],
  (val) => {
    const parsed = val !== undefined && val !== null && val !== '' ? Number(val) : null
    if (parsed !== props.modelValue) {
      emit('update:modelValue', parsed)
    }
  },
)
</script>

<template>
  <div class="bill-status-filter">
    <div
      v-if="title"
      class="bill-status-filter__title"
    >
      <span>{{ title }}</span>
    </div>

    <ElRadioGroup
      v-model="activeValue"
      size="default"
      class="bill-status-filter__group"
    >
      <ElRadioButton
        v-for="opt in computedOptions"
        :key="String(opt.value ?? '')"
        :value="opt.value ?? ''"
      >
        <span class="bill-status-filter__item">
          <span
            v-if="opt.color"
            class="status-indicator-dot"
            :style="{ backgroundColor: opt.color }"
          />
          <span class="status-indicator-label">{{ opt.label }}</span>
        </span>
      </ElRadioButton>
    </ElRadioGroup>
  </div>
</template>

<style lang="scss" scoped>
.bill-status-filter {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
  flex-wrap: wrap;

  &__title {
    font-size: 13px;
    font-weight: 600;
    color: var(--el-text-color-secondary);
    white-space: nowrap;
    display: flex;
    align-items: center;
    gap: 4px;

    &::after {
      content: ':';
    }
  }

  &__group {
    display: inline-flex;
    flex-wrap: wrap;

    :deep(.el-radio-button__inner) {
      padding: 7px 14px;
      font-size: 13px;
      border-radius: 6px;
      transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
    }
  }

  &__item {
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }
}

.status-indicator-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  display: inline-block;
  box-shadow: 0 0 0 1.5px rgba(255, 255, 255, 0.8);
  flex-shrink: 0;
}
</style>
