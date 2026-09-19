<script lang="ts" setup generic="E extends IIdentifiableEntity, S extends AbstractCurdService<E>">
import type { CSSProperties } from 'vue'
import type { AbstractCurdService, IIdentifiableEntity, ISearchField, ITableColumn } from '@/framework'
import type { UseTableReturn } from '@/hook/kubo/useTable'
// 显式 import 而不是只靠 `unplugin-vue-components` 自动导入：
// 视图中出现 <el-*> 就依赖全局注册，在 vitest（不加载该插件）与任何独立挂载场景下
// 都会退化成一个未知元素 —— 表格列的 `#default="{ row }"` 拿不到 row，直接抛错。
import { ElButton, ElEmpty, ElForm, ElFormItem, ElInput, ElPagination, ElTable, ElTableColumn } from 'element-plus'
import { computed, nextTick, ref, shallowRef, useSlots, watch } from 'vue'
import { getCellColor, getCellText, getColumnAlign, getDictionary, getSearchConfigList, getTableConfigList } from '@/framework'

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
 *
 * ## 为什么不做成“上帝组件”
 * airpower 的 `ATable` 有 40+ 个 prop、内部还管弹窗与权限，改一处怕碰别处。
 * 这里把职责切干净：**KTable 只负责“显示 + 交互回传”**，CRUD、Dialog 由 `useTable` 与页面决定。
 *
 * ## 用法
 * ```vue
 * <KTable :table="table" :permissions="{ add, edit, delete }" @edit="openEditor" />
 * ```
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
  /**
   * 是否隐藏「删除」按钮。
   *
   * 为什么需要它：`v-permission` 遇到未传值（`undefined`）时是**不拦截**的（见 `directives/permission.ts`），
   * 所以「没传 delete 权限码」不等于「不显示删除」—— 实体没有删除端点时必须显式关掉，
   * 否则用户会看到一个点下去必然报错的按钮（如移库单：后端 `@Extends(exclude = Curd.Delete)`）。
   */
  hideDelete?: boolean
  /** 是否隐藏「编辑」按钮（纯只读列表用，如操作日志） */
  hideEdit?: boolean
  /**
   * 是否隐藏「启用/禁用」按钮。
   *
   * 单据类实体没有启用/禁用语义（状态由状态机决定），传 `false` 时同样会因为
   * 未传 `enable` 权限码而照常渲染 —— 与 `hideDelete` 同理。
   */
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
  /**
   * 该行是否禁止「添加子级」（树表）；返回 true 时按钮置灰。
   *
   * 判据同样来自业务：系统内置权限（`isSystem`）不允许挂子权限 ——
   * 它们是后端 `@Permission` 注解的镜像，从界面加子级只会造成两边不一致。
   */
  disableAddChild?: (row: E) => boolean
  /**
   * 该行是否禁止编辑；返回 true 时「编辑」按钮置灰。
   *
   * 与 `disableDelete` 一样，判据来自**实体自身的状态机**（如合同只有"未生效"可改），
   * 由页面传入 —— 组件不认识业务状态。
   */
  disableEdit?: (row: E) => boolean
  /**
   * 是否显示导出按钮。
   *
   * 导出由**后端的异步导出任务**完成（`ExportUtil`），点击后走 `useTable` 的 `onExport`；
   * 页面只要在 `useTable(..., { export: true })` 里声明，并把 `permissions.export` 传进来。
   */
  showExport?: boolean
  /**
   * 不显示的列（按 `@KTable` 的字段名）。
   *
   * 用于「同一张表在不同维度下要的列不同」的场景（库存按仓库看时不显示生产单元列，
   * 按生产单元看时不显示仓库列）。做成 prop 而不是让页面自己 `getTableConfigList()`
   * 过滤：`KTable` 的列、搜索项、单元格规则都读同一份元数据，页面绕过去容易两边不一致。
   */
  excludeColumns?: string[]
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
}>()

/** 插槽声明（同 KFormDialog：泛型组件不声明插槽时页面用不了） */
defineSlots<{
  /** 工具条左侧（新增按钮之后） */
  'toolbar-left'?: (props: { selection: E[] }) => unknown
  /** 工具条右侧（刷新按钮之前） */
  'toolbar-right'?: (props: { selection: E[] }) => unknown
  /**
   * 覆盖任意列的单元格渲染：`#cell-<字段名>`（如 `#cell-purchasePrice`）。
   *
   * 需要它的场景是「列的显示不只看值」：金额要先查脱敏策略、链接要拼静态资源前缀、
   * 状态要带图标。默认渲染走 `getCellText`（字典/日期/金额/脱敏同一套规则），
   * 只有默认规则不够时才用插槽覆盖 —— 不是每个字段都要写一个插槽。
   */
  [key: `cell-${string}`]: (props: { row: E, value: unknown, column: ITableColumn }) => unknown
}>()

const EntityClass = computed(() => props.table.entityClass as unknown as object)

/** 页面是否覆盖了该列的单元格渲染（`useSlots` 而不是模板里的 `$slots[...]`：动态键在模板里类型不友好） */
const slots = useSlots()
function hasCellSlot(key: string): boolean {
  return !!slots[`cell-${key}`]
}

/** 导出中（按钮 loading；导出的进度只有后端知道，这里只表达"还没回来"） */
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

/** 列：来自 `@Table` 元数据（框架层注册表），可被 `excludeColumns` 裁掉几列 */
const columns = computed<ITableColumn[]>(() => {
  const all = getTableConfigList(EntityClass.value)
  return props.excludeColumns?.length
    ? all.filter(column => !props.excludeColumns!.includes(column.key))
    : all
})

/** 搜索项：来自 `@Search` 元数据 */
const searchFields = computed<ISearchField[]>(() => getSearchConfigList(EntityClass.value))

/** 树模式：数据来自 `getList`（树实体），用 Element Plus 的树表格渲染（不分页） */
const isTree = computed(() => props.table.isTree.value)

/** 搜索表单草稿：编辑中不直接影响列表，点「搜索」才提交（否则每个字符都触发请求） */
const searchDraft = ref<Record<string, unknown>>({})

watch(
  searchFields,
  (fields) => {
    const draft: Record<string, unknown> = {}
    for (const field of fields) {
      draft[field.key] = (props.table.filter.value as Record<string, unknown>)[field.key] ?? ''
    }
    searchDraft.value = draft
  },
  { immediate: true },
)

const hasSearch = computed(() => searchFields.value.length > 0)

function onSearch() {
  const next: Record<string, unknown> = {}
  for (const [key, value] of Object.entries(searchDraft.value)) {
    // 空串统一转 undefined：后端把空串当"等于空字符串"过滤时会查不到任何数据
    if (value !== '' && value !== null && value !== undefined)
      next[key] = value
  }
  props.table.search(next)
}

function onResetSearch() {
  searchDraft.value = Object.fromEntries(searchFields.value.map(field => [field.key, '']))
  props.table.reset()
}

/** 单元格文本：金额/日期/脱敏/关联对象/字典由框架层统一处理 */
function cellText(row: E, column: ITableColumn): string {
  return getCellText(row, column, getDictionary(EntityClass.value, column.key))
}

/** 状态色：字典带颜色时给单元格上色（数据密集场景靠颜色扫读，比读文字快） */
function cellStyle(row: E, column: ITableColumn): CSSProperties {
  const color = getCellColor(getDictionary(EntityClass.value, column.key), (row as unknown as Record<string, unknown>)[column.key])
  return color ? { color } : {}
}

/**
 * 已选行。
 * 用 `shallowRef`：`ref<E[]>` 会被深度解包成 `UnwrapRefSimple<E>[]`，
 * 传给插槽（声明为 `E[]`）或 emit 时会报「E 可能是别的子类型」。
 */
const selection = shallowRef<E[]>([])
/**
 * ElTable 实例（只用到 `toggleRowSelection`）。
 * 不能写 `InstanceType<typeof ElTable>`：EP 的表格是**泛型函数组件**，`InstanceType`
 * 需要 `new (...)` 签名，直接报 TS2344。
 */
interface IElTableInstance {
  toggleRowSelection: (row: unknown, selected?: boolean) => void
}
const tableRef = ref<IElTableInstance>()

function onSelectionChange(rows: E[]) {
  selection.value = rows
  emit('selectionChanged', rows)
}

/** 回显已选：数据到达后按 id 勾选（选择器再次打开时能看到上次选的） */
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

/**
 * ElTable 的 sort-change 载荷（`prop: string | null`，不是可选字段 —— 直接内联箭头函数
 * 会因为 `prop?: string` 与 EP 的类型不兼容而再次触发 TS2322）。
 */
interface ITableSortPayload {
  prop: string | null
  order: 'ascending' | 'descending' | null
}

/** 列头排序 → useTable 的语义化排序（`ascending/descending` → `desc` 布尔） */
function onSortChange(payload: ITableSortPayload) {
  props.table.onSortChanged(payload.prop && payload.order ? { field: payload.prop, desc: payload.order === 'descending' } : null)
}

/** 行是否处于禁用态（用于「启用/禁用」按钮的切换与置灰） */
function isDisabled(row: E): boolean {
  return !!(row as unknown as { isDisabled?: boolean }).isDisabled
}

/** 该行是否还能删除（树模式下有子级不能删，否则会留下孤儿节点） */
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

const tableHeight = computed(() => props.height ?? undefined)

/** 空态文案：区分「本来就没有」与「筛选后没有」 */
const emptyText = computed(() =>
  Object.keys(props.table.filter.value).length
    ? '没有符合条件的记录，试试放宽筛选条件'
    : '暂无数据',
)
</script>

<template>
  <div class="k-table">
    <!-- 搜索区：条件不自动提交，避免每个字符打一次接口 -->
    <ElForm
      v-if="hasSearch"
      class="k-table__search"
      :inline="true"
      @submit.prevent="onSearch"
    >
      <ElFormItem
        v-for="field in searchFields"
        :key="field.key"
        :label="field.label"
        class="k-table__search-item"
      >
        <ElInput
          v-model="searchDraft[field.key] as string"
          :placeholder="`请输入${field.label}`"
          clearable
          @keyup.enter="onSearch"
        />
      </ElFormItem>
      <ElFormItem class="k-table__search-actions">
        <ElButton
          type="primary"
          @click="onSearch"
        >
          搜索
        </ElButton>
        <ElButton @click="onResetSearch">
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
          @click="emit('add')"
        >
          新增
        </ElButton>
        <slot
          name="toolbar-left"
          :selection="selection"
        />
      </div>
      <div class="k-table__toolbar-right">
        <ElButton
          v-if="showExport"
          v-permission="permissions?.export"
          :loading="isExporting"
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
          @click="table.reload()"
        >
          刷新
        </ElButton>
      </div>
    </div>

    <!-- 表格：data 直接来自 TanStack Query，加载中用 loading 覆盖而不是清空 data（避免整块闪空） -->
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
          <!-- 页面用 `#cell-<字段名>` 覆盖该列的渲染（金额脱敏、链接、图标等） -->
          <slot
            v-if="hasCellSlot(column.key)"
            v-bind="{ row, column, value: (row as E)[column.key as keyof E] }"
            :name="`cell-${column.key}`"
          />
          <span
            v-else
            :style="cellStyle(row as E, column)"
          >{{ cellText(row as E, column) }}</span>
        </template>
      </ElTableColumn>

      <ElTableColumn
        v-if="hasRowActions"
        label="操作"
        width="200"
        fixed="right"
        align="center"
      >
        <template #default="{ row }">
          <ElButton
            v-for="action in actions ?? []"
            :key="action.label"
            v-permission="action.permission"
            link
            type="primary"
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
            :disabled="disableEdit?.(row as E)"
            @click="emit('edit', row as E)"
          >
            编辑
          </ElButton>
          <ElButton
            v-if="!hideEnable"
            v-permission="permissions?.enable"
            link
            :type="isDisabled(row as E) ? 'success' : 'warning'"
            @click="table.onToggleEnable((row as E).id, isDisabled(row as E))"
          >
            {{ isDisabled(row as E) ? '启用' : '禁用' }}
          </ElButton>
          <ElButton
            v-if="!hideDelete"
            v-permission="permissions?.delete"
            link
            type="danger"
            :disabled="isDeleteDisabled(row as E)"
            @click="table.onDelete((row as E).id)"
          >
            删除
          </ElButton>
        </template>
      </ElTableColumn>

      <template #empty>
        <ElEmpty :description="emptyText" />
      </template>
    </ElTable>

    <!-- 分页：条数变化时回到第 1 页（否则会停在不存在的页码，表格空白但没人知道为什么）；
         树数据不分页，隐藏分页器 -->
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
  gap: 12px;
}

.k-table__search {
  padding: 12px 16px 0;
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
}

.k-table__search-item {
  margin-right: 16px;
  margin-bottom: 12px;
}

.k-table__search-actions {
  margin-bottom: 12px;
}

.k-table__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.k-table__toolbar-left,
.k-table__toolbar-right {
  display: flex;
  gap: 8px;
  align-items: center;
}

.k-table__pagination {
  display: flex;
  justify-content: flex-end;
}
</style>
