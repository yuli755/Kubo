<script lang="ts" setup>
import { ElCard } from 'element-plus'

/**
 * # KPanel —— 内容卡片（替代 airpower `APanel`）
 *
 * ## 为什么单独做一层
 * 原库 `APanel` 同时管「标题栏 + 右侧操作区 + 内容留白 + 滚动容器」，27 个页面在用它，
 * 但每个页面只用到了其中一部分。这里保留最小可用集合：**标题（可选）+ 右上角插槽 + 内容**，
 * 其余交给页面（留白交给布局，滚动交给 `Console` 的工作区）。
 *
 * ## 用法
 * ```vue
 * <KPanel title="仓库">
 *   <template #extra><ElButton>导出</ElButton></template>
 *   <KTable :table="table" />
 * </KPanel>
 * ```
 *
 * 不传 `title` 时渲染成无头卡片（只有留白与边框），替代「裸 div」做页面外壳。
 */

withDefaults(defineProps<{
  /** 标题（不传则不渲染标题栏） */
  title?: string
  /** 副标题/说明 */
  description?: string
  /** 是否显示边框与内边距（列表页外壳用 true，纯容器用 false） */
  bordered?: boolean
  /** 内容是否贴边（表格自带留白时用 true，避免双层 padding） */
  flush?: boolean
}>(), {
  bordered: true,
  flush: false,
})
</script>

<template>
  <ElCard
    class="k-panel"
    :class="{ 'k-panel--flush': flush, 'k-panel--plain': !bordered }"
    shadow="never"
  >
    <template
      v-if="title || $slots.title || $slots.extra"
      #header
    >
      <div class="k-panel__header">
        <div class="k-panel__heading">
          <slot name="title">
            <span class="k-panel__title">{{ title }}</span>
          </slot>
          <span
            v-if="description"
            class="k-panel__description"
          >{{ description }}</span>
        </div>
        <div class="k-panel__extra">
          <slot name="extra" />
        </div>
      </div>
    </template>

    <slot />
  </ElCard>
</template>

<style scoped>
.k-panel {
  border-radius: var(--el-border-radius-base);
}

.k-panel--plain {
  border: none;
}

/* 表格页：卡片不额外加内边距，由 KTable 自己控制，避免「框里再套一个框」 */
.k-panel--flush :deep(.el-card__body) {
  padding: 12px 16px;
}

.k-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.k-panel__heading {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
}

.k-panel__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.k-panel__description {
  overflow: hidden;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.k-panel__extra {
  display: flex;
  gap: 8px;
  align-items: center;
}
</style>
