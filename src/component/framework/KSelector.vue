<script lang="ts" setup generic="E extends IIdentifiableEntity, S extends AbstractCurdService<E>">
import type { AbstractCurdService, IIdentifiableEntity, ITransformerConstructor } from '@/framework'
import { ElButton, ElDialog } from 'element-plus'
import { computed, shallowRef, watch } from 'vue'
import { getModelName } from '@/framework'
import { useTable } from '@/hook/kubo/useTable'
import KTable from './KTable.vue'

/**
 * # KSelector —— 通用实体选择器（替代 airpower `ASelector` + `DialogProps.select*`）
 *
 * ## 为什么要重做
 * 原库把「选择器」拆成三层：`ASelector`（搜索框 + 表格）、`DialogProps.withSelector`（静态方法生成
 * 弹窗参数）、`DialogUtil.select/selectList`（命令式打开）。三层各有各的约定，调用方要记住
 * 「用 select 还是 selectList、param 还是 selectList、单选返回值还是数组」——16 处调用写法各不相同。
 *
 * 这里收敛成**一个组件 + 两个事件**：
 * - 打开：`v-model="visible"`；
 * - 选择：`@confirm="(rows) => ..."`（单选传 1 个元素的数组，`multiple` 决定上限）；
 * - 回显：`:selected="已选实体数组"`（组件内部按 id 勾选，无需调用方 toggle）。
 *
 * ## 用法
 * ```vue
 * <KSelector
 *   v-model="picking"
 *   :service="StorageService"
 *   :selected="form.storageList"
 *   multiple
 *   title="选择仓库"
 *   @confirm="rows => form.storageList = rows"
 * />
 * ```
 *
 * ## 与旧选择器的互操作
 * 存量 `StorageSelector.vue` 等仍是 airpower 组件（`DialogProps.withParam`）。
 * 新页面要打开它们时用 `@/framework` 的 `DialogUtil.show/select/selectList` —— 那份实现
 * 的 props/events 与 `DialogProps` 契约一致，因此**可以混用**，不需要一次性全量重写。
 */

const props = withDefaults(defineProps<{
  modelValue: boolean
  /** 服务类（决定查询哪个实体、列怎么显示）；`entityClass` 参与类型推断，见 `useTable` 注释 */
  service: new () => S & { entityClass: ITransformerConstructor<E> }
  /** 是否多选 */
  multiple?: boolean
  /** 回显的已选实体（按 id 勾选） */
  selected?: E[]
  /** 弹窗标题（缺省为「选择{模型名}」） */
  title?: string
  /** 固定过滤条件（如只让选某类型的仓库） */
  filter?: Partial<E>
  /**
   * 树形实体（`useTable` 的 `tree: true`）：部门/仓库这类后端返回嵌套树的实体，
   * 打开按层级展示并支持展开——否则选「研发部」时看不到它下属的「前端组」。
   */
  tree?: boolean
  /** 缓存键（同域共享；缺省按服务类名生成） */
  queryKey?: string[]
  /** 表格高度 */
  height?: string | number
  /** 弹窗宽度 */
  width?: string
}>(), {
  multiple: false,
  height: 380,
  width: '720px',
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

/**
 * 实例化服务类。
 * 服务类来自 props（运行时名字），`new props.service()` 会被 `new-cap` 误判，
 * 统一在参数名大写的 helper 里 new 一次。
 */
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

/**
 * 当前勾选（单选时只保留最后一个，避免用户手滑框选两行）。
 * 用 `shallowRef` 而不是 `ref`：`ref<E[]>([])` 会被 Vue 深度解包成 `UnwrapRefSimple<E>[]`，
 * emit 到 `(rows: E[]) => void` 时报「E 可能是别的子类型」（实体里可能带 ref 字段）。
 */
const picking = shallowRef<E[]>([])

function onSelectionChanged(rows: E[]) {
  picking.value = props.multiple ? rows : rows.slice(-1)
}

/** 每次打开重置勾选，并按 `selected` 勾选（避免上次的残留带进来） */
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
</script>

<template>
  <ElDialog
    v-model="visible"
    :title="title ?? `选择${getModelName(entityClass as unknown as object)}`"
    :width="width"
    append-to-body
    destroy-on-close
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
      :selected-ids="picking.map(item => item.id)"
      @selection-changed="onSelectionChanged"
    />

    <template #footer>
      <span class="k-selector__hint">
        {{ multiple ? `已选 ${picking.length} 项` : picking.length ? '已选 1 项' : '请选择一条记录' }}
      </span>
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
    </template>
  </ElDialog>
</template>

<style scoped>
.k-selector__hint {
  float: left;
  font-size: 12px;
  line-height: 32px;
  color: var(--el-text-color-secondary);
}
</style>
