<script lang="ts" setup generic="E extends IIdentifiableEntity, S extends AbstractCurdService<E>">
import type { AbstractCurdService, IIdentifiableEntity, ITransformerConstructor } from '@/framework'
import { ElDescriptions, ElDescriptionsItem, ElEmpty } from 'element-plus'
import { computed } from 'vue'
import { getCellText, getDecoratedFieldList, getDictionary, getFieldLabel, getTableConfig } from '@/framework'
import { useDetail } from '@/hook/kubo/useDetail'

/**
 * # KDetail —— 只读详情（替代 airpower `useDetail` + `ADialog` + `AFormField` 的组合）
 *
 * ## 为什么按 id 重新查，而不是用列表带过来的实体
 * 存量详情页的数据来自 `DialogUtil.show(Detail, row)`：**列表里那一份**。别人改了状态、
 * 价格、审核人之后，详情页展示的是过期快照 —— 对单据类页面这是会出事故的。
 * 这里按 id 走 `getDetail` 重新拉，并交给 TanStack 缓存。
 *
 * ## 字段从哪来
 * `@Field` 装饰过的**全部字段**（含 `@Table({ hide: true })` 的列与不在表格里的关联对象），
 * 按 `@Table` 的 order 排前面、其余保持声明序。页面可用 `#field-<key>` 覆盖任意一项。
 *
 * ## 用法
 * ```vue
 * <KDetail :service="InputService" :id="id" title="入库单详情" :column="3" />
 * ```
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
  // 去掉内部保留键与表单专用字段（`__model__` 这类）
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
  // 详情用表格列配置里的格式化规则（金额/日期/脱敏/关联对象与列表完全一致）
  const config = getTableConfig(EntityClass.value, key) ?? { key }
  return getCellText(entity, config, getDictionary(EntityClass.value, key))
}
</script>

<template>
  <div class="k-detail">
    <div
      v-if="title"
      class="k-detail__title"
    >
      {{ title }}
    </div>

    <ElEmpty
      v-if="!detail.isLoading.value && !detail.entity.value"
      description="没有找到该记录，可能已被删除"
    />

    <ElDescriptions
      v-else
      v-loading="detail.isLoading.value"
      :column="column"
      border
      size="default"
    >
      <ElDescriptionsItem
        v-for="key in visibleFields"
        :key="key"
        :label="fieldLabel(key)"
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
.k-detail__title {
  margin-bottom: 12px;
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
</style>
