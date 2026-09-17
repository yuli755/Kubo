<script lang="ts" setup generic="E extends IIdentifiableEntity">
import type { CSSProperties } from 'vue'
import type { IIdentifiableEntity, ITableColumn } from '@/framework'
import { ElButton, ElEmpty, ElTable, ElTableColumn } from 'element-plus'
import { computed, useSlots } from 'vue'
import { getCellColor, getCellText, getColumnAlign, getDictionary, getTableConfigList } from '@/framework'

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
   */
  entity: object
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
  /** 行操作列宽 */
  actionWidth?: number | string
  /** 表格高度（不传则自适应内容） */
  maxHeight?: string | number
}>(), {
  emptyText: '暂无明细',
  showIndex: true,
  actionLabel: '操作',
  actionWidth: 160,
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
      :width="actionWidth"
      fixed="right"
      align="center"
    >
      <template #default="{ row }">
        <ElButton
          v-for="action in rowActions"
          :key="action.label"
          v-permission="action.permission"
          link
          :type="action.danger ? 'danger' : 'primary'"
          :disabled="action.disabled?.(row as E)"
          @click.stop="action.handler(row as E)"
        >
          {{ action.label }}
        </ElButton>
      </template>
    </ElTableColumn>

    <template #empty>
      <ElEmpty :description="emptyText" />
    </template>
  </ElTable>
</template>

<style lang="scss" scoped>
.k-detail-table {
  width: 100%;
}
</style>
