<script lang="ts" setup>
import { ElCard } from 'element-plus'

/**
 * # KPanel —— 内容卡片（替代 airpower `APanel`）
 *
 * ## 为什么单独做一层
 * 原库 `APanel` 同时管「标题栏 + 右侧操作区 + 内容留白 + 滚动容器」，27 个页面在用它，
 * 但每个页面只用到了其中一部分。这里保留最小可用集合：**标题（可选）+ 右上角插槽 + 内容**，
 * 其余交给页面（留白交给布局，滚动交给 `Console` 的工作区）。
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
          <div class="k-panel__bar" />
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
  border-radius: 10px;
  border: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color);
  box-shadow: var(--kubo-shadow-sm);
  transition: all var(--kubo-duration-base) ease;
}

.k-panel--plain {
  border: none;
  background: transparent;
  box-shadow: none;
}

/* 表格页：卡片内边距贴合现代流式视觉 */
.k-panel--flush :deep(.el-card__body) {
  padding: 14px 18px;
}

.k-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.k-panel__heading {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.k-panel__bar {
  width: 3px;
  height: 15px;
  border-radius: 2px;
  background: var(--el-color-primary);
  flex-shrink: 0;
}

.k-panel__title {
  font-size: 15px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  line-height: 1.2;
}

.k-panel__description {
  overflow: hidden;
  font-size: 13px;
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
