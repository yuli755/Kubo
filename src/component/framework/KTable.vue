<script lang="ts" setup generic="E extends IIdentifiableEntity, S extends AbstractCurdService<E>">
import type { CSSProperties } from 'vue'
import type { AbstractCurdService, IIdentifiableEntity, ISearchField, ITableColumn } from '@/framework'
import type { UseTableReturn } from '@/hook/kubo/useTable'
// 显式 import 而不是只靠 `unplugin-vue-components` 自动导入：
// 视图中出现 <el-*> 就依赖全局注册，在 vitest（不加载该插件）与任何独立挂载场景下
// 都会退化成一个未知元素 —— 表格列的 `#default=\"{ row }\"` 拿不到 row，直接抛错。
import { ElButton, ElEmpty, ElForm, ElFormItem, ElInput, ElPagination, ElTable, ElTableColumn } from 'element-plus'
import { computed, ref, watch } from 'vue'
import { getDictionary, getSearchConfigList, getTableConfigList } from '@/framework'

/**
 * # KTable —— 自研表格（替代 airpower `ATable`）
 *
 * ## 设计取舍（与 airpower 版本的差异，逐条都是有理由的）
 *
 * | 点 | airpower `ATable` | 这里 |
 * | --- | --- | --- |
 * | 列/搜索项 | 组件内部读 airpower 的元数据注册表 | 读 `src/framework` 的注册表（**同一份**装饰器写的） |
 * | 数据 | 组件直接读写 `useHook.request` / `response` | 只读 `list/total/isLoading`，条件通过 `search/onPageChanged/onSortChanged` 回传 |
 * | 行操作 | 内置 add/edit/delete/enable/disable 按钮 + `permission` 判断权限码 | 同样内置，但权限码由页面传入（`permissions` 对象），组件不认识业务常量 |
 * | 空态 | 自动文案 | 区分"没数据"与"有筛选但没匹配"（后者要引导用户改条件，而不是让人以为表是空的） |
 *
 * ## 为什么不做成"上帝组件"
 * airpower 的 `ATable` 有 40+ 个 prop、内部还管弹窗与权限，改一处怕碰别处。
 * 这里把职责切干净：**KTable 只负责"显示 + 交互回传"**，CRUD、Dialog 由 `useTable` 与页面决定。
 *
 * ## 用法
 * ```vue
 * <KTable :table="table" :permissions="{ add, edit, delete }" />
 * ```
 */

type DictLike = { getLabel?: (value: unknown) => string, getColor?: (value: unknown) => string } | undefined

interface RowAction {
  /** 权限码（不传则不校验） */
  permission?: string
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
  /** 行操作权限码（add/edit/delete/enable/disable） */
  permissions?: Record<string, string | undefined>
  /** 是否隐藏新增按钮 */
  hideAdd?: boolean
  /** 是否隐藏刷新按钮 */
  hideRefresh?: boolean
  /** 是否显示序号列 */
  showIndex?: boolean
  /** 是否可选（多选） */
  selectable?: boolean
  /** 额外的行操作（在编辑/删除之前展示） */
  actions?: RowAction[]
  /** 表格高度（默认自适应容器） */
  height?: string | number
  /** 行数据的唯一键字段 */
  rowKey?: string
}>(), {
  showIndex: true,
  hideRefresh: false,
  rowKey: 'id',
})

const emit = defineEmits<{
  (event: 'selectionChanged', rows: E[]): void
  (event: 'edit', row: E): void
  (event: 'detail', row: E): void
  (event: 'add'): void
}>()

const EntityClass = computed(() => props.table.entityClass as unknown as object)

/** 列：来自 `@Table` 元数据（框架层注册表） */
const columns = computed<ITableColumn[]>(() => getTableConfigList(EntityClass.value))

/** 搜索项：来自 `@Search` 元数据 */
const searchFields = computed<ISearchField[]>(() => getSearchConfigList(EntityClass.value))

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

/** 单元格文本：字典字段显示 label，日期字段格式化，其余直接转字符串 */
function cellText(row: E, column: ITableColumn): string {
  const value = (row as unknown as Record<string, unknown>)[column.key]
  if (value === null || value === undefined || value === '')
    return '-'
  const dictionary = getDictionary(EntityClass.value, column.key) as DictLike
  if (dictionary?.getLabel) {
    const label = dictionary.getLabel(value)
    if (label)
      return label
  }
  if (typeof value === 'boolean')
    return value ? '是' : '否'
  return String(value)
}

/** 状态色：字典带颜色时给单元格上色（数据密集场景靠颜色扫读，比读文字快） */
function cellStyle(row: E, column: ITableColumn): CSSProperties {
  const dictionary = getDictionary(EntityClass.value, column.key) as DictLike
  const value = (row as unknown as Record<string, unknown>)[column.key]
  const color = dictionary?.getColor?.(value)
  return color ? { color } : {}
}

const selection = ref<E[]>([])

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

function onSelectionChange(rows: E[]) {
  selection.value = rows
  emit('selectionChanged', rows)
}

/** 行是否处于禁用态（用于「启用/禁用」按钮的切换与置灰） */
function isDisabled(row: E): boolean {
  return !!(row as unknown as { isDisabled?: boolean }).isDisabled
}

async function onAdd() {
  emit('add')
}

function onEdit(row: E) {
  emit('edit', row)
}

const hasRowActions = computed(
  () => true, // 编辑/删除始终存在；是否可用由权限码决定
)

const tableHeight = computed(() => props.height ?? undefined)
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
          @click="onAdd"
        >
          新增
        </ElButton>
        <slot
          name="toolbar-left"
          :selection="selection"
        />
      </div>
      <div class="k-table__toolbar-right">
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
      v-loading="table.isLoading.value"
      :data="table.list.value"
      :row-key="rowKey"
      :height="tableHeight"
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
        :sortable="column.sortable ? 'custom' : false"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <span :style="cellStyle(row as E, column)">{{ cellText(row as E, column) }}</span>
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
            v-permission="permissions?.edit"
            link
            type="primary"
            @click="onEdit(row as E)"
          >
            编辑
          </ElButton>
          <ElButton
            v-permission="permissions?.enable"
            link
            :type="isDisabled(row as E) ? 'success' : 'warning'"
            @click="table.onToggleEnable((row as E).id, isDisabled(row as E))"
          >
            {{ isDisabled(row as E) ? '启用' : '禁用' }}
          </ElButton>
          <ElButton
            v-permission="permissions?.delete"
            link
            type="danger"
            @click="table.onDelete((row as E).id)"
          >
            删除
          </ElButton>
        </template>
      </ElTableColumn>

      <template #empty>
        <ElEmpty :description="Object.keys(table.filter.value).length ? '没有符合条件的记录，试试放宽筛选条件' : '暂无数据'" />
      </template>
    </ElTable>

    <!-- 分页：条数变化时回到第 1 页（否则会停在不存在的页码，表格空白但没人知道为什么） -->
    <div class="k-table__pagination">
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
