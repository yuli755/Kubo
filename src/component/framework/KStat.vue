<script lang="ts" setup>
/**
 * # KStat —— 列表上方的统计条（替代"页面自己写一排 ElTag"）
 *
 * 数据来自 `useResourceCount`（后端 `{baseUrl}/getCount`）。设计上有三条克制的约定：
 *
 * 1. **可点项才可点**：带 `key` 的项点击后把 `[field]: key` 交给页面去筛表，没有 `key` 的
 *    项（纯总数）渲染成静态文本 —— 避免"看起来能点、点了没反应"；
 * 2. **当前生效的筛选项高亮**（`active`）：统计条与表格是同一份 filter，必须能对得上；
 * 3. **拿不到数就显示 `-`**：单项失败（无权限 / 网络）不隐藏该项 —— "看不到数"与"这个状态没有单"
 *    是两回事，藏起来会让人误判。
 *
 * ## 用法
 * ```vue
 * <KStat :items="counts.cells.value" @select="onStatSelect" />
 * ```
 */
defineProps<{
  /** 统计项（`counts.cells`） */
  items: Array<{ field: string, key?: number | string, label: string, color?: string, value: number | null, active?: boolean }>
  /** 加载中（保留上一次的数字，只是变淡） */
  loading?: boolean
}>()

const emit = defineEmits<{
  /** 点击某个可点项（值为该项的 `key`，页面据此筛表） */
  (event: 'select', key: number | string): void
}>()

/** 只有带 `key` 的项可点（模板里用函数，避免内联多语句箭头） */
function onItemClick(item: { key?: number | string }): void {
  if (item.key !== undefined) {
    emit('select', item.key)
  }
}
</script>

<template>
  <div
    class="k-stat"
    :class="{ 'k-stat--loading': loading }"
  >
    <button
      v-for="item in items"
      :key="`${item.field}:${item.key ?? '*'}`"
      class="k-stat__item"
      :class="{ 'k-stat__item--clickable': item.key !== undefined, 'k-stat__item--active': item.active }"
      type="button"
      @click="onItemClick(item)"
    >
      <span
        v-if="item.color"
        class="k-stat__dot"
        :style="{ backgroundColor: item.color }"
      />
      <span class="k-stat__label">{{ item.label }}</span>
      <span class="k-stat__value">{{ item.value === null ? '-' : item.value }}</span>
    </button>
  </div>
</template>

<style lang="scss" scoped>
.k-stat {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
  transition: opacity 0.2s;
}

.k-stat--loading {
  opacity: 0.65;
}

.k-stat__item {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  padding: 4px 10px;
  font-size: 13px;
  color: var(--el-text-color-primary);
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
}

.k-stat__item--clickable {
  cursor: pointer;
}

.k-stat__item--clickable:hover {
  border-color: var(--el-color-primary-light-5);
}

.k-stat__item--active {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary);
}

.k-stat__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.k-stat__label {
  color: var(--el-text-color-regular);
}

.k-stat__value {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}
</style>
