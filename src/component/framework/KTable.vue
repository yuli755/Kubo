<script lang="ts" setup generic="E extends IIdentifiableEntity, S extends AbstractCurdService<E>">
import type { CSSProperties } from 'vue'
import type { AbstractCurdService, IIdentifiableEntity, ISearchField, ITableColumn } from '@/framework'
import type { UseTableReturn } from '@/hook/kubo/useTable'
import {
  Download,
  Operation,
  Plus,
  RefreshLeft,
  RefreshRight,
  Search,
} from '@element-plus/icons-vue'
import {
  ElButton,
  ElCheckbox,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElOption,
  ElPagination,
  ElPopover,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTooltip,
} from 'element-plus'
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  shallowRef,
  useSlots,
  watch,
} from 'vue'
import { getCellColor, getCellText, getColumnAlign, getDictionary, getEnumItems, getSearchConfigList, getTableConfigList } from '@/framework'
import { estimateActionColumnWidth } from './tableActionWidth'

/**
 * # KTable —— 自研表格（替代 airpower `ATable`）
 *
 * ## 设计取舍（与 airpower 版本的差异，逐条都是有理由的）
 *
 * | 点 | airpower `ATable` | 这里 |
 * | --- | --- | --- |
 * | 列/搜索项 | 组件内部读 airpower 的元数据注册表 | 读 `src/framework` 的注册表（**同一份**装饰器写的） |
 * | 数据 | 组件直接读写 `useHook.request` / `response` | 只读 `list/total/isLoading`，条件通过 `search/onPageChanged/onSortChanged` 回传 |
 * | 行操作 | 内置 add/edit/delete/enable 按钮 + `permission` 判断权限码 | 同样内置，但权限码由页面传入（`permissions` 对象），组件不认识业务常量 |
 * | 单元格 | 组件内一堆 if | 统一走 `@/framework` 的 `getCellText`（金额/日期/脱敏/关联对象/字典同一套规则） |
 * | 空态 | 自动文案 | 区分“没数据”与“有筛选但没匹配”（后者要引导用户改条件，而不是让人以为表是空的） |
 */

interface RowAction {
  /** 权限码（不传则不校验；数组表示「满足其一即可」，与 `v-permission` 同口径） */
  permission?: string | string[]
  /** 显示的文案 */
  label: string
  /** 处理函数（收到整行） */
  handler: (row: E) => void | Promise<void>
  /** 是否禁用（如已禁用的记录不能再禁用） */
  disabled?: (row: E) => boolean
}

const props = withDefaults(defineProps<{
  /** `useTable` 的返回值 */
  table: UseTableReturn<E, S>
  /** 行操作权限码（add/addChild/edit/delete/enable） */
  permissions?: Record<string, string | undefined>
  /** 是否隐藏新增按钮 */
  hideAdd?: boolean
  /** 是否隐藏刷新按钮 */
  hideRefresh?: boolean
  /** 是否隐藏整列行操作（选择器场景用） */
  hideActions?: boolean
  /** 是否隐藏「删除」按钮 */
  hideDelete?: boolean
  /** 是否隐藏「编辑」按钮（纯只读列表用，如操作日志） */
  hideEdit?: boolean
  /** 是否隐藏「启用/禁用」按钮 */
  hideEnable?: boolean
  /** 是否显示序号列 */
  showIndex?: boolean
  /** 是否可选（多选） */
  selectable?: boolean
  /** 已选中的 id（选择器回显用；变化后自动勾选对应行） */
  selectedIds?: number[]
  /** 额外的行操作（在编辑/删除之前展示） */
  actions?: RowAction[]
  /** 表格高度（默认自适应容器） */
  height?: string | number
  /** 行数据的唯一键字段 */
  rowKey?: string
  /** 该行是否禁止删除（如树节点下有子级）；返回 true 时删除按钮置灰 */
  disableDelete?: (row: E) => boolean
  /** 该行是否禁止「添加子级」（树表）；返回 true 时按钮置灰 */
  disableAddChild?: (row: E) => boolean
  /** 该行是否禁止编辑；返回 true 时「编辑」按钮置灰 */
  disableEdit?: (row: E) => boolean
  /**
   * 是否显示导出按钮。
   *
   * 通常**不必传**：未传时自动取 `useTable({ export: true })` 的 `canExport`
   * （单一数据源，页面只配一处）。传 `false` 可强制隐藏。
   */
  showExport?: boolean
  /** 不显示的列（按 `@KTable` 的字段名） */
  excludeColumns?: string[]
  /** 自定义空态文案（不传则按有无筛选条件自动生成） */
  emptyText?: string
}>(), {
  showIndex: true,
  hideRefresh: false,
  hideActions: false,
  hideDelete: false,
  hideEnable: false,
  rowKey: 'id',
})

const emit = defineEmits<{
  (event: 'selectionChanged', rows: E[]): void
  (event: 'edit', row: E): void
  (event: 'detail', row: E): void
  (event: 'add'): void
  (event: 'addChild', row: E): void
  (event: 'rowDblclick', row: E): void
}>()

/** 插槽声明 */
defineSlots<{
  /** 工具条左侧（新增按钮之后） */
  'toolbar-left'?: (props: { selection: E[] }) => unknown
  /** 工具条右侧（刷新按钮之前） */
  'toolbar-right'?: (props: { selection: E[] }) => unknown
  /** 批量操作插槽（勾选多行时展示） */
  'batch-actions'?: (props: { selection: E[] }) => unknown
  /** 空态自定义插槽 */
  'empty'?: () => unknown
  /** 覆盖任意列的单元格渲染 */
  [key: `cell-${string}`]: (props: { row: E, value: unknown, column: ITableColumn }) => unknown
}>()

const EntityClass = computed(() => props.table.entityClass as unknown as object)

const slots = useSlots()
function hasCellSlot(key: string): boolean {
  return !!slots[`cell-${key}`]
}

/** 导出中 */
const isExporting = ref(false)

async function onExportClick() {
  isExporting.value = true
  try {
    await props.table.onExport()
  }
  finally {
    isExporting.value = false
  }
}

/** 列：来自 `@Table` 元数据 */
const rawColumns = computed<ITableColumn[]>(() => {
  const all = getTableConfigList(EntityClass.value)
  return props.excludeColumns?.length
    ? all.filter(column => !props.excludeColumns!.includes(column.key))
    : all
})

const storageColumnKey = computed(() => {
  const qk = props.table.queryKey?.join(':') || 'default'
  return `kubo:table:columns:${qk}`
})

/**
 * 元数据声明「默认隐藏」的列（`@Table({ hide: true })`）。
 *
 * `getTableConfigList` 刻意不剔除 hide 列（见 `framework/__tests__/metadata.spec.ts:62`：
 * 「hide 只是默认隐藏，仍在列里（列选择器可恢复）」），剔除动作归这里。
 * 曾经这里的 `columns` 只减用户勾掉的键、从不读 `col.hide`，于是 hide 的语义整条丢失：
 * 列级脱敏页的 `resource` 关联列被声明为 hide，却照样渲染出来，
 * 而它没有标 `payload`，单元格就成了 `[object Object]`（真机实测，Playwright 抓到的首行文本）。
 */
const defaultHiddenColumnKeys = computed<string[]>(
  () => rawColumns.value.filter(col => col.hide).map(col => col.key),
)

const hiddenColumnKeys = ref<string[]>([])

function loadColumnPreferences() {
  try {
    const saved = localStorage.getItem(storageColumnKey.value)
    if (saved) {
      const parsed = JSON.parse(saved)
      if (Array.isArray(parsed)) {
        // 用户已经为这张表表达过偏好：完全以他勾的为准（包括把 hide 列勾出来），
        // 不再叠加元数据默认值 —— 否则「显示某列」这个动作刷新一次就被抹掉。
        hiddenColumnKeys.value = parsed
        return
      }
    }
  }
  catch {
    // ignore
  }
  // 没有偏好记录时用元数据默认：hide 的列不出现（列选择器里仍可勾回来）
  hiddenColumnKeys.value = [...defaultHiddenColumnKeys.value]
}

loadColumnPreferences()

watch(storageColumnKey, () => {
  loadColumnPreferences()
})

function toggleColumn(key: string, visible: boolean) {
  if (visible) {
    hiddenColumnKeys.value = hiddenColumnKeys.value.filter(k => k !== key)
  }
  else {
    if (!hiddenColumnKeys.value.includes(key)) {
      hiddenColumnKeys.value = [...hiddenColumnKeys.value, key]
    }
  }
  try {
    localStorage.setItem(storageColumnKey.value, JSON.stringify(hiddenColumnKeys.value))
  }
  catch {
    // ignore
  }
}

function resetColumns() {
  // 「重置」= 回到元数据声明的默认，而不是「全部显示」
  hiddenColumnKeys.value = [...defaultHiddenColumnKeys.value]
  try {
    localStorage.removeItem(storageColumnKey.value)
  }
  catch {
    // ignore
  }
}

const columns = computed<ITableColumn[]>(() => {
  return rawColumns.value.filter(col => !hiddenColumnKeys.value.includes(col.key))
})

/** 搜索项：来自 `@Search` 元数据 */
const searchFields = computed<ISearchField[]>(() => getSearchConfigList(EntityClass.value))

/** 树模式 */
const isTree = computed(() => props.table.isTree.value)

/** 搜索表单草稿 */
const searchDraft = ref<Record<string, unknown>>({})

watch(
  [searchFields, () => props.table.filter.value],
  ([fields, filter]) => {
    const draft: Record<string, unknown> = {}
    for (const field of fields) {
      draft[field.key] = (filter as Record<string, unknown>)[field.key] ?? (field.dictionary ? undefined : '')
    }
    searchDraft.value = draft
  },
  { immediate: true, deep: true },
)

const hasSearch = computed(() => searchFields.value.length > 0)

function onSearch() {
  const next: Record<string, unknown> = {}
  for (const [key, value] of Object.entries(searchDraft.value)) {
    if (value !== '' && value !== null && value !== undefined) {
      next[key] = value
    }
  }
  props.table.search(next)
}

function onResetSearch() {
  searchDraft.value = Object.fromEntries(
    searchFields.value.map(field => [field.key, field.dictionary ? undefined : '']),
  )
  props.table.reset()
}

const firstSearchInputRef = ref<InstanceType<typeof ElInput> | null>(null)

function onGlobalKeydown(e: KeyboardEvent) {
  if (
    e.key === '/'
    && !['INPUT', 'TEXTAREA', 'SELECT'].includes((e.target as HTMLElement)?.tagName)
    && !(e.target as HTMLElement)?.isContentEditable
  ) {
    if (searchFields.value.length > 0 && firstSearchInputRef.value) {
      e.preventDefault()
      firstSearchInputRef.value.focus()
    }
  }
}

onMounted(() => {
  window.addEventListener('keydown', onGlobalKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onGlobalKeydown)
})

/** 单元格文本 */
function cellText(row: E, column: ITableColumn): string {
  return getCellText(row, column, getDictionary(EntityClass.value, column.key))
}

/** 状态色 */
function cellStyle(row: E, column: ITableColumn): CSSProperties {
  const color = getCellColor(getDictionary(EntityClass.value, column.key), (row as unknown as Record<string, unknown>)[column.key])
  return color ? { color } : {}
}

const selection = shallowRef<E[]>([])

interface IElTableInstance {
  toggleRowSelection: (row: unknown, selected?: boolean) => void
  clearSelection: () => void
  /** ElTable 的根元素（`.el-table`），量树表高度用 */
  $el?: HTMLElement
}
const tableRef = ref<IElTableInstance>()

function onSelectionChange(rows: E[]) {
  selection.value = rows
  emit('selectionChanged', rows)
}

function clearSelection() {
  tableRef.value?.clearSelection()
  selection.value = []
  emit('selectionChanged', [])
}

const isAllSelected = computed(() => {
  const current = props.table.list.value
  if (!current.length || !selection.value.length) {
    return false
  }
  const selectedIdSet = new Set(selection.value.map(s => s.id))
  return current.every(row => selectedIdSet.has(row.id))
})

function toggleSelectAll() {
  if (!tableRef.value) {
    return
  }
  if (isAllSelected.value) {
    clearSelection()
  }
  else {
    for (const row of props.table.list.value) {
      tableRef.value.toggleRowSelection(row as never, true)
    }
  }
}

defineExpose({
  clearSelection,
  toggleSelectAll,
  tableRef,
})

const hasActiveFilter = computed(() => {
  const f = props.table.filter.value as Record<string, unknown>
  return Object.values(f).some(v => v !== '' && v !== null && v !== undefined)
})

function isMonospaceColumn(column: ITableColumn): boolean {
  const key = column.key.toLowerCase()
  return (
    key.includes('code')
    || key.includes('number')
    || key.includes('no')
    || key.includes('sn')
    || key.includes('identity')
    || key.includes('phone')
    || key.includes('ip')
  )
}

/** 回显已选 */
watch(
  [() => props.selectedIds, () => props.table.list.value, tableRef],
  async () => {
    if (!props.selectable || !props.selectedIds?.length) {
      return
    }
    await nextTick()
    const ids = new Set(props.selectedIds)
    for (const row of props.table.list.value) {
      if (ids.has(row.id)) {
        tableRef.value?.toggleRowSelection(row as never, true)
      }
    }
  },
  { immediate: true },
)

interface ITableSortPayload {
  prop: string | null
  order: 'ascending' | 'descending' | null
}

function onSortChange(payload: ITableSortPayload) {
  props.table.onSortChanged(payload.prop && payload.order ? { field: payload.prop, desc: payload.order === 'descending' } : null)
}

function isDisabled(row: E): boolean {
  return !!(row as unknown as { isDisabled?: boolean }).isDisabled
}

function isDeleteDisabled(row: E): boolean {
  if (props.disableDelete?.(row)) {
    return true
  }
  if (isTree.value) {
    const children = (row as unknown as { children?: E[] }).children
    return !!children?.length
  }
  return false
}

const hasRowActions = computed(() => !props.hideActions)

/**
 * 树表自动高度：让 Element Plus 用**自己的**内部滚动 + 原生吸顶表头。
 *
 * 为什么不能给表头做 sticky（已实测否决）：`.el-table__header-wrapper` 往上四层祖先
 * `.el-card__body`(overflow:auto)、`.el-card`(hidden)、`.el-table`(hidden)、`.k-table__body`(hidden)
 * **全是滚动容器**，`position: sticky` 只会相对最近的那一层解析，而那几层自己不滚（高度随内容长），
 * 于是注入 sticky 后计算样式确实是 `sticky`，309 行的权限树滚 600px 表头 top 照样从 279 变成 -321。
 * 覆盖这四处 overflow 会波及全部 26 个 KTable 使用点和 `KSelector.vue` 对话框里的表格，不划算。
 *
 * 传进去的必须是**数字**：EP 的 `parseHeight` 只认数字或 `/^\d+(?:px)?$/`，
 * `calc(100vh - 320px)` 会被当成 auto，等于没传。
 * 页面显式给了 `height` 就一律听页面的（`KSelector` 之类窄容器里的表格不该吃视口高度）。
 */
const MIN_TREE_TABLE_HEIGHT = 360
const treeAutoHeight = ref<number | undefined>(undefined)

function measureTreeHeight() {
  if (!isTree.value || props.height !== undefined) {
    treeAutoHeight.value = undefined
    return
  }
  const el = tableRef.value?.$el
  if (!el?.getBoundingClientRect) {
    return
  }
  // 56 = `.k-shell__footer` 35 + `.k-shell__content` 下内边距 20 + 1px 余量
  const available = Math.round(window.innerHeight - el.getBoundingClientRect().top - 56)
  treeAutoHeight.value = Math.max(MIN_TREE_TABLE_HEIGHT, available)
}

const tableHeight = computed(() => props.height ?? treeAutoHeight.value ?? undefined)

onMounted(() => {
  if (isTree.value && props.height === undefined) {
    void nextTick(measureTreeHeight)
    window.addEventListener('resize', measureTreeHeight)
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', measureTreeHeight)
})

/**
 * 操作列宽度：按**按钮文字**估宽，不再按按钮个数拍档位。
 *
 * 估宽算法与 `KDetailTable` 共用 `./tableActionWidth`（两份实现必然漂移）。
 * 原实现是 `count<=1→90, <=2→140, <=3→190, <=4→230, else min(count*58, 360)`，
 * 对 2 字标签严重超配 —— 实测采购单 5 个按钮（查看/审核/驳回/再来一单/编辑）的文字只需要
 * 174px，操作列却占 290px；合同页更是 348px。1131px 的可用宽度里被操作列吃掉 26%~31%，
 * 这正是采购/销售/合同三页横向滚动的最大单一成因（数据列本身的内容宽都远小于列宽）。
 * 改后复测：采购单操作列 290 → 230、`.k-table__actions` 内容宽 205.2（= 230 − 24 内边距）。
 *
 * 现在 = Σ 按钮文字宽 + (n-1)×8px 间距 + 24px 单元格左右内边距，并夹在 [90, 360]。
 * 配套把样式里 `.el-button + .el-button` 的 12px 兄弟外边距清零（间距统一交给 `gap`），
 * 否则两套间距叠加，实测每个间隔 23.3px 而不是 8px。
 *
 * 计数口径与按钮渲染口径保持一致：`actions` + 树表的「添加子级」+ 未隐藏的编辑/启停/删除。
 * `v-permission` 可能让某个按钮对当前用户不渲染，这里仍按全部计入（宁宽勿窄，
 * 与改动前的行为一致）。
 */
const actionsColumnWidth = computed(() => {
  const labels: string[] = (props.actions ?? []).map(action => action.label)
  if (isTree.value) {
    labels.push('添加子级')
  }
  if (!props.hideEdit) {
    labels.push('编辑')
  }
  if (!props.hideEnable) {
    // 启用/禁用 都是两个字，取任一即可
    labels.push('启用')
  }
  if (!props.hideDelete) {
    labels.push('删除')
  }
  return estimateActionColumnWidth(labels, { gap: 8 })
})

/** 空态文案 */
const emptyText = computed(() => {
  if (props.emptyText) {
    return props.emptyText
  }
  return Object.keys(props.table.filter.value).length
    ? '没有符合条件的记录，试试放宽筛选条件'
    : '暂无数据'
})
</script>

<template>
  <div class="k-table">
    <!-- 搜索区 -->
    <ElForm
      v-if="hasSearch"
      class="k-table__search"
      :inline="true"
      @submit.prevent="onSearch"
    >
      <div class="k-table__search-fields">
        <ElFormItem
          v-for="(field, idx) in searchFields"
          :key="field.key"
          :label="field.label"
          class="k-table__search-item"
        >
          <ElSelect
            v-if="field.dictionary"
            v-model="searchDraft[field.key] as string | number | undefined"
            :placeholder="`请选择${field.label}`"
            clearable
            :style="{ width: field.width ? (typeof field.width === 'number' ? `${field.width}px` : field.width) : '150px' }"
            @change="onSearch"
          >
            <ElOption
              v-for="option in getEnumItems(field.dictionary)"
              :key="String(option.key)"
              :label="option.label"
              :value="option.key"
            />
          </ElSelect>
          <ElInput
            v-else
            :ref="el => { if (idx === 0) firstSearchInputRef = el as InstanceType<typeof ElInput> }"
            v-model="searchDraft[field.key] as string"
            :placeholder="idx === 0 ? `请输入${field.label} (按 / 聚焦)` : `请输入${field.label}`"
            :prefix-icon="Search"
            clearable
            @keyup.enter="onSearch"
          />
        </ElFormItem>
      </div>
      <ElFormItem class="k-table__search-actions">
        <ElButton
          type="primary"
          :icon="Search"
          @click="onSearch"
        >
          搜索
        </ElButton>
        <ElButton
          :icon="RefreshLeft"
          @click="onResetSearch"
        >
          重置
        </ElButton>
      </ElFormItem>
    </ElForm>

    <!-- 工具条 -->
    <div class="k-table__toolbar">
      <div class="k-table__toolbar-left">
        <ElButton
          v-if="!hideAdd"
          v-permission="permissions?.add"
          type="primary"
          :icon="Plus"
          @click="emit('add')"
        >
          新增
        </ElButton>
        <template v-if="selectable && table.list.value.length > 0">
          <ElButton
            size="small"
            plain
            @click="toggleSelectAll"
          >
            {{ isAllSelected ? '取消全选' : '本页全选' }}
          </ElButton>
          <ElButton
            v-if="selection.length > 0"
            size="small"
            plain
            type="info"
            @click="clearSelection"
          >
            清空已选 ({{ selection.length }})
          </ElButton>
        </template>
        <slot
          name="toolbar-left"
          :selection="selection"
        />
      </div>
      <div class="k-table__toolbar-right">
        <ElButton
          v-if="showExport ?? table.canExport.value"
          v-permission="permissions?.export"
          :loading="isExporting"
          :icon="Download"
          plain
          @click="onExportClick"
        >
          导出
        </ElButton>
        <slot
          name="toolbar-right"
          :selection="selection"
        />
        <ElButton
          v-if="!hideRefresh"
          :loading="table.isLoading.value"
          :icon="RefreshRight"
          @click="table.reload()"
        >
          刷新
        </ElButton>
        <ElPopover
          trigger="click"
          placement="bottom-end"
          :width="200"
        >
          <template #reference>
            <ElTooltip
              content="列显隐设置"
              placement="top"
            >
              <ElButton
                :icon="Operation"
                circle
                aria-label="列显隐设置"
              />
            </ElTooltip>
          </template>
          <div class="k-table__col-settings">
            <div class="k-table__col-settings-header">
              <span class="setting-title">自定义显示列</span>
              <ElButton
                type="primary"
                link
                size="small"
                @click="resetColumns"
              >
                重置
              </ElButton>
            </div>
            <div class="k-table__col-settings-list">
              <ElCheckbox
                v-for="col in rawColumns"
                :key="col.key"
                :model-value="!hiddenColumnKeys.includes(col.key)"
                :disabled="col.force"
                class="col-checkbox"
                @change="val => toggleColumn(col.key, !!val)"
              >
                {{ col.label }}
              </ElCheckbox>
            </div>
          </div>
        </ElPopover>
      </div>
    </div>

    <!-- 批量选中浮动提示条 -->
    <Transition name="fade-slide">
      <div
        v-if="selectable && selection.length > 0"
        class="k-table__selection-bar k-slide-down"
      >
        <div class="k-table__selection-info">
          <span class="selection-pill">{{ selection.length }}</span>
          <span class="selection-label">项已选定</span>
        </div>
        <div class="k-table__selection-actions">
          <slot
            name="batch-actions"
            :selection="selection"
          />
          <ElButton
            link
            size="small"
            type="primary"
            @click="clearSelection"
          >
            清空已选
          </ElButton>
        </div>
      </div>
    </Transition>

    <!-- 表格本体 -->
    <ElTable
      ref="tableRef"
      v-loading="table.isLoading.value"
      :data="table.list.value"
      :row-key="rowKey"
      :height="tableHeight"
      :default-expand-all="isTree"
      :tree-props="{ children: 'children' }"
      border
      stripe
      class="k-table__body"
      @selection-change="onSelectionChange"
      @sort-change="onSortChange"
      @row-dblclick="row => emit('rowDblclick', row as E)"
    >
      <ElTableColumn
        v-if="selectable"
        type="selection"
        width="46"
        :reserve-selection="true"
      />
      <ElTableColumn
        v-if="showIndex"
        type="index"
        label="#"
        width="56"
        align="center"
      />
      <ElTableColumn
        v-for="column in columns"
        :key="column.key"
        :prop="column.key"
        :label="column.label"
        :width="column.width"
        :min-width="column.minWidth ?? 120"
        :align="getColumnAlign(column)"
        :sortable="column.sortable ? 'custom' : false"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <slot
            v-if="hasCellSlot(column.key)"
            v-bind="{ row, column, value: (row as E)[column.key as keyof E] }"
            :name="`cell-${column.key}`"
          />
          <span
            v-else
            :class="{ 'font-mono': isMonospaceColumn(column) }"
            :style="cellStyle(row as E, column)"
          >{{ cellText(row as E, column) }}</span>
        </template>
      </ElTableColumn>

      <ElTableColumn
        v-if="hasRowActions"
        label="操作"
        :width="actionsColumnWidth"
        fixed="right"
        align="center"
      >
        <template #default="{ row }">
          <div class="k-table__actions">
            <ElButton
              v-for="action in actions ?? []"
              :key="action.label"
              v-permission="action.permission"
              link
              type="primary"
              size="small"
              :disabled="action.disabled?.(row as E)"
              @click="action.handler(row as E)"
            >
              {{ action.label }}
            </ElButton>
            <ElButton
              v-if="isTree"
              v-permission="permissions?.addChild"
              link
              type="primary"
              size="small"
              :disabled="disableAddChild?.(row as E)"
              @click="emit('addChild', row as E)"
            >
              添加子级
            </ElButton>
            <ElButton
              v-if="!hideEdit"
              v-permission="permissions?.edit"
              link
              type="primary"
              size="small"
              :disabled="disableEdit?.(row as E)"
              @click="emit('edit', row as E)"
            >
              编辑
            </ElButton>
            <ElButton
              v-if="!hideEnable"
              v-permission="permissions?.enable"
              link
              size="small"
              :type="isDisabled(row as E) ? 'success' : 'warning'"
              @click="table.onToggleEnable((row as E).id, isDisabled(row as E))"
            >
              {{ isDisabled(row as E) ? '启用' : '禁用' }}
            </ElButton>
            <ElButton
              v-if="!hideDelete"
              v-permission="permissions?.delete"
              link
              size="small"
              type="danger"
              :disabled="isDeleteDisabled(row as E)"
              @click="table.onDelete((row as E).id)"
            >
              删除
            </ElButton>
          </div>
        </template>
      </ElTableColumn>

      <template #empty>
        <slot name="empty">
          <div class="k-table__empty-wrapper">
            <ElEmpty
              :description="emptyText"
              :image-size="80"
            >
              <ElButton
                v-if="hasActiveFilter"
                class="k-table__empty-reset"
                size="small"
                :icon="RefreshLeft"
                @click="onResetSearch"
              >
                清空筛选条件
              </ElButton>
            </ElEmpty>
          </div>
        </slot>
      </template>
    </ElTable>

    <!-- 分页器 -->
    <div
      v-if="!isTree"
      class="k-table__pagination"
    >
      <ElPagination
        :current-page="table.page.value"
        :page-size="table.pageSize.value"
        :total="table.total.value"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @current-change="(page: number) => table.onPageChanged(page)"
        @size-change="(size: number) => table.onPageChanged(1, size)"
      />
    </div>
  </div>
</template>

<style scoped>
.k-table {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.k-table__search {
  padding: 16px 18px 4px;
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  box-shadow: var(--kubo-shadow-sm);
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.k-table__search-fields {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  flex: 1;
}

.k-table__search-item {
  margin-right: 0;
  margin-bottom: 12px;
}

.k-table__search-actions {
  margin-right: 0;
  margin-bottom: 12px;
  display: flex;
  gap: 8px;
}

.k-table__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 2px;
}

.k-table__toolbar-left,
.k-table__toolbar-right {
  display: flex;
  gap: 10px;
  align-items: center;
}

.k-table__col-settings {
  display: flex;
  flex-direction: column;
}

.k-table__col-settings-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.k-table__col-settings-header .setting-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.k-table__col-settings-list {
  max-height: 240px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  padding-top: 6px;
}

.k-table__col-settings-list .col-checkbox {
  margin-right: 0;
  height: 28px;
}

.k-table__selection-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-7);
  border-radius: 8px;
}

.k-table__selection-info {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.selection-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 10px;
  background: var(--el-color-primary);
  /* 主色底上的白字：--el-color-white 亮暗恒定（同 WarehouseHeatmap 格内白字口径），
     写死 #fff 会被 designTokenContract 的颜色断言挡住 */
  color: var(--el-color-white);
  font-size: 12px;
  font-weight: 600;
  font-family: var(--kubo-font-mono);
}

.selection-label {
  font-weight: 500;
}

.k-table__selection-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.k-table__empty-wrapper {
  padding: 16px 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.k-table__empty-reset {
  margin-top: 10px;
}

.k-table__body {
  border-radius: 8px;
  overflow: hidden;
}

.k-table__body :deep(.el-table__header-wrapper th) {
  background-color: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
  font-weight: 600;
  font-size: 13px;
  height: 44px;
}

.k-table__body :deep(.el-table__row) {
  height: 46px;
  font-variant-numeric: tabular-nums;
}

.k-table__actions {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: nowrap;
  gap: 8px;
}

/* Element Plus 给相邻按钮加了 12px 兄弟外边距，与上面的 gap 叠加后实测每个间隔 23.3px，
   操作列因此比按钮文字真正需要的宽出约 50px（采购单 5 个按钮：文字 174px、列宽 290px）。
   间距统一由 gap 管，这里清零，`actionsColumnWidth` 的估宽才和真实渲染对得上。 */
.k-table__actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.k-table__pagination {
  display: flex;
  justify-content: flex-end;
  padding-top: 6px;
}
</style>
