<script lang="ts" setup generic="E extends IIdentifiableEntity">
import type { CSSProperties } from 'vue'
import type { IIdentifiableEntity, ITableColumn } from '@/framework'
import { ElButton, ElEmpty, ElTable, ElTableColumn } from 'element-plus'
import { computed, useSlots } from 'vue'
import { getCellColor, getCellText, getColumnAlign, getDictionary, getTableConfigList } from '@/framework'
import { estimateActionColumnWidth } from './tableActionWidth'

/**
 * # KDetailTable —— 内存明细表（单据明细 / 参与方 / 附件这类"不在服务端分页里的数组"）
 *
 * ## 为什么不能用 `KTable`
 * `KTable` 的契约是 `table: UseTableReturn`（服务 + TanStack 查询 + 分页 + 搜索）。
 * 单据编辑器里的明细是**表单对象上的一个数组**（还没提交给后端），既没有服务也没有分页；
 * 硬塞进 `KTable` 只会得到一个到处是 `if` 的组件。
 *
 * ## 列从哪来
 * 与 `KTable` 同一份元数据（`@KTable` 注册表）+ 同一套单元格规则（`getCellText`/`getCellColor`），
 * 所以明细表和列表的显示口径一致：字典染色、金额千分位、时间格式都不会两边不一样。
 * 页面用 `exclude` 去掉不想要的列（如 `createTime`）。
 *
 * ## 行操作
 * `rowActions` 的 `permission` / `disabled` / `handler` 与 `KTable` 的 `actions` 同形：
 * 组件不认识业务状态（如"只有移动中才能作业"），判据由页面给。
 *
 * ## 用法
 * ```vue
 * <KDetailTable
 *   :entity="MoveDetailEntity"
 *   :rows="formData.details"
 *   :exclude="['createTime']"
 *   :row-actions="[{ label: '删除', danger: true, handler: removeDetail }]"
 * />
 * ```
 */

interface DetailRowAction {
  /** 权限码（不传则不校验；数组表示「满足其一即可」，与 `v-permission` 同口径） */
  permission?: string | string[]
  /** 按钮文案 */
  label: string
  /** 处理函数（收到整行） */
  handler: (row: E) => void | Promise<void>
  /** 是否禁用（如单据状态不允许作业） */
  disabled?: (row: E) => boolean
  /** 危险色（删除类操作） */
  danger?: boolean
}

const props = withDefaults(defineProps<{
  /**
   * 实体类（读 `@KTable` 元数据）。
   * 传类而不是实例：装饰器把元数据写在与 `constructor` 关联的注册表里。
   *
   * 不能写成 `entity: object`：type-based 声明把它编译成 `type: Object`，
   * 而类构造器运行时是 Function，于是 Vue 每次挂载都误报
   * `Invalid prop: type check failed for prop "entity". Expected Object, got Function`。
   * 构造签名（TSConstructorType）编译器推不出运行期类型，编译为 `type: null`
   * （跳过运行期校验，实测 vue 3.5.25 compiler-sfc），但 TS 侧仍只收类构造器，
   * 比原来那个一直在撒谎的 `object` 更诚实。
   */
  entity: abstract new (...args: never[]) => E
  /** 明细行 */
  rows: E[]
  /** 不显示的列（如 `createTime`） */
  exclude?: string[]
  /** 行操作 */
  rowActions?: DetailRowAction[]
  /** 空态文案 */
  emptyText?: string
  /** 是否显示序号列 */
  showIndex?: boolean
  /** 行操作列标题 */
  actionLabel?: string
  /** 行操作列宽；不传则按按钮文字估（见下 `resolvedActionWidth`） */
  actionWidth?: number | string
  /** 表格高度（不传则自适应内容） */
  maxHeight?: string | number
}>(), {
  emptyText: '暂无明细',
  showIndex: true,
  actionLabel: '操作',
})

const emit = defineEmits<{
  (event: 'rowClick', row: E): void
}>()

/** 插槽声明：与 `KTable` 同款，页面用 `#cell-<字段名>` 覆盖单元格渲染 */
defineSlots<{
  [key: `cell-${string}`]: (props: { row: E, value: unknown, column: ITableColumn }) => unknown
}>()

const slots = useSlots()
function hasCellSlot(key: string): boolean {
  return !!slots[`cell-${key}`]
}

const columns = computed<ITableColumn[]>(() =>
  getTableConfigList(props.entity).filter(column => !(props.exclude ?? []).includes(column.key)),
)

/**
 * 操作列宽：页面显式传了 `actionWidth` 就听页面的，否则按按钮文字估。
 *
 * 改前默认写死 160px，而采购单明细抽屉里那一列只有一个「作业」按钮（文字 30px），
 * 实测整个明细表 Σ 列宽 1306 vs 抽屉内可用 1120 ⇒ 横向溢出 186px，这 100px 白扔的
 * 操作列宽是其中最大的一块。
 *
 * 间距传 12 而不是 `KTable` 的 8：这里的按钮直接排在单元格里，没有
 * `.k-table__actions` 那种 `display:flex; gap` 容器，用的是 Element Plus 自己的
 * `.el-button + .el-button { margin-left: 12px }`。
 * 下限 60：2 字按钮 30px 文字 + 24px 单元格内边距 = 54，表头「操作」要 48。
 */
const resolvedActionWidth = computed(() =>
  props.actionWidth
  ?? estimateActionColumnWidth((props.rowActions ?? []).map(action => action.label), { gap: 12, min: 60 }),
)

function cellText(row: E, column: ITableColumn): string {
  return getCellText(row, column, getDictionary(props.entity, column.key))
}

function cellStyle(row: E, column: ITableColumn): CSSProperties {
  const color = getCellColor(getDictionary(props.entity, column.key), (row as unknown as Record<string, unknown>)[column.key])
  return color ? { color } : {}
}
</script>

<template>
  <ElTable
    :data="rows"
    :max-height="maxHeight"
    border
    stripe
    class="k-detail-table"
    @row-click="row => emit('rowClick', row as E)"
  >
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
          :style="cellStyle(row as E, column)"
        >{{ cellText(row as E, column) }}</span>
      </template>
    </ElTableColumn>

    <ElTableColumn
      v-if="rowActions?.length"
      :label="actionLabel"
      :width="resolvedActionWidth"
      fixed="right"
      align="center"
    >
      <template #default="{ row, $index }">
        <!-- $index >= 0 的守卫：ElTableColumn 挂载时会用 `slots.default({ row: {}, $index: -1 })`
             做一次子列探测渲染（element-plus table-column/index.vue…lang.mjs:97-103），
             在 .hidden-columns 里留下一个绑定到空对象 `{}` 的重复按钮。visibility:hidden 下
             真人点不到，但自动化点击（offsetParent 判定漏过 visibility）会命中它，
             handler 收到 `{}` —— 排查时酷似"点了没反应的 Vue bug"。真实渲染 $index 必 >= 0。 -->
        <ElButton
          v-for="action in rowActions"
          :key="action.label"
          v-permission="action.permission"
          link
          :type="action.danger ? 'danger' : 'primary'"
          :disabled="action.disabled?.(row as E)"
          @click.stop="$index >= 0 && action.handler(row as E)"
        >
          {{ action.label }}
        </ElButton>
      </template>
    </ElTableColumn>

    <template #empty>
      <div class="k-detail-table__empty">
        <ElEmpty
          :description="emptyText"
          :image-size="64"
        />
      </div>
    </template>
  </ElTable>
</template>

<style lang="scss" scoped>
.k-detail-table {
  width: 100%;
  border-radius: var(--kubo-radius-sm, 4px);
  overflow: hidden;

  :deep(.el-table__header th) {
    background: var(--el-fill-color-light);
    color: var(--el-text-color-primary);
    font-weight: 600;
    font-size: 13px;
    height: 42px;
  }

  :deep(.el-table__row) {
    /* 兜底值必须与 tokens.scss 定义同值（120ms）：兜底写 150ms 只在令牌缺失时生效，
       而缺失已被 designTokenContract 的死引用守卫拦截——漂移兜底唯一的实际作用是误导读者 */
    transition: background-color var(--kubo-duration-fast, 120ms) ease;
  }
}

.k-detail-table__empty {
  padding: 16px 0;
}
</style>
