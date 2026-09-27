<script lang="ts" setup generic="E extends IIdentifiableEntity, S extends AbstractCurdService<E>">
import type { AbstractCurdService, IIdentifiableEntity, ITransformerConstructor } from '@/framework'
import { ElButton, ElDialog, ElTag } from 'element-plus'
import { computed, shallowRef, watch } from 'vue'
import { getModelName } from '@/framework'
import { useTable } from '@/hook/kubo/useTable'
import KTable from './KTable.vue'

/**
 * # KSelector —— 通用实体选择器（替代 airpower `ASelector` + `DialogProps.select*`）
 *
 * ## 契约收敛
 * - 打开：`v-model="visible"`；
 * - 选择：`@confirm="(rows) => ..."`（单选传 1 个元素的数组，`multiple` 决定上限）；
 * - 回显：`:selected="已选实体数组"`（组件内部按 id 勾选，无需调用方 toggle）；
 * - 快捷交互：支持双击行快速确认选择。
 */

const props = withDefaults(defineProps<{
  modelValue: boolean
  /** 服务类（决定查询哪个实体、列怎么显示） */
  service: new () => S & { entityClass: ITransformerConstructor<E> }
  /** 是否多选 */
  multiple?: boolean
  /** 回显的已选实体（按 id 勾选） */
  selected?: E[]
  /** 弹窗标题（缺省为「选择{模型名}」） */
  title?: string
  /** 固定过滤条件 */
  filter?: Partial<E>
  /** 树形实体 */
  tree?: boolean
  /** 缓存键 */
  queryKey?: string[]
  /** 表格高度 */
  height?: string | number
  /** 弹窗宽度 */
  width?: string
}>(), {
  multiple: false,
  height: 380,
  width: '740px',
})

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'confirm', rows: E[]): void
  (event: 'closed'): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value),
})

function createService(ServiceClass: new () => S): S {
  return new ServiceClass()
}

const serviceInstance = createService(props.service)
const entityClass = serviceInstance.entityClass

const table = useTable(props.service, {
  queryKey: props.queryKey ?? [getModelName(entityClass as unknown as object), 'selector'],
  defaultFilter: props.filter,
  service: serviceInstance,
  tree: props.tree,
})

const picking = shallowRef<E[]>([])

function onSelectionChanged(rows: E[]) {
  picking.value = props.multiple ? rows : rows.slice(-1)
}

function onRowDblClick(row: E) {
  if (!props.multiple) {
    picking.value = [row]
    onConfirm()
  }
}

watch(
  () => props.modelValue,
  (opened) => {
    if (opened) {
      picking.value = [...(props.selected ?? [])]
      table.reload()
    }
  },
)

function onConfirm() {
  if (!picking.value.length) {
    return
  }
  emit('confirm', picking.value)
  visible.value = false
}

const modelName = computed(() => getModelName(entityClass as unknown as object))

const selectorEmptyText = computed(() => {
  const hasFilter = Object.keys(table.filter.value).length > 0
  if (hasFilter) {
    return `没有符合条件的${modelName.value}，试试放宽筛选条件`
  }
  return `暂无可选的${modelName.value}数据，请先前往主数据模块维护`
})

const hintText = computed(() => {
  if (picking.value.length) {
    return props.multiple ? `已选择 ${picking.value.length} 项` : '已选定 1 条记录（双击可快速确认）'
  }
  if (!table.isLoading.value && table.total.value === 0) {
    return `暂无可选择的${modelName.value}`
  }
  return props.multiple ? '请勾选需要选择的记录' : '请勾选记录或双击单行'
})
</script>

<template>
  <ElDialog
    v-model="visible"
    :title="title ?? `选择${modelName}`"
    :width="width"
    append-to-body
    destroy-on-close
    class="k-selector-dialog"
    @closed="emit('closed')"
  >
    <KTable
      :table="table"
      :height="height"
      :selectable="true"
      :hide-actions="true"
      :hide-add="true"
      :hide-refresh="false"
      :show-index="true"
      :empty-text="selectorEmptyText"
      :selected-ids="picking.map(item => item.id)"
      @selection-changed="onSelectionChanged"
      @row-dblclick="onRowDblClick"
    />

    <template #footer>
      <div class="k-selector__footer">
        <div class="k-selector__hint">
          <ElTag
            v-if="picking.length"
            size="small"
            type="primary"
            effect="plain"
          >
            {{ picking.length }} 项已选
          </ElTag>
          <span class="hint-text">{{ hintText }}</span>
        </div>
        <div class="k-selector__actions">
          <ElButton @click="visible = false">
            取消
          </ElButton>
          <ElButton
            type="primary"
            :disabled="!picking.length"
            @click="onConfirm"
          >
            确定
          </ElButton>
        </div>
      </div>
    </template>
  </ElDialog>
</template>

<style scoped>
.k-selector__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  gap: 12px;
}

.k-selector__hint {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.k-selector__actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
</style>
