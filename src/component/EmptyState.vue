<script lang="ts" setup>
/**
 * # 通用空状态组件（V7 · 反馈统一）
 *
 * 统一各列表/图表/看板的空态视觉：一致的插画尺寸、间距、深浅色配色与可选 CTA。
 * 内部复用 `el-empty`（已在 element-plus-aggregate 导出），只在外面套一层统一的尺寸/间距约定，
 * 不新增任何判定逻辑。
 *
 * ## 用法
 * ```vue
 * <EmptyState description="暂无已登记资源">
 *   <template #action>
 *     <el-button type="primary">去登记</el-button>
 *   </template>
 * </EmptyState>
 * ```
 */

interface Props {
  /** 空态说明文案 */
  description?: string
  /** 插画尺寸（px），默认 80 */
  imageSize?: number
}

const props = withDefaults(defineProps<Props>(), {
  description: '暂无数据',
  imageSize: 80,
})
</script>

<template>
  <div class="empty-state">
    <el-empty
      :description="props.description"
      :image-size="props.imageSize"
    >
      <slot name="action" />
    </el-empty>
  </div>
</template>

<style lang="scss" scoped>
.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  min-height: 160px;
  padding: var(--kubo-spacing-5);
}

/* 空态文案统一弱化，深浅色下都靠 EP 文本变量（不硬编码灰） */
:deep(.el-empty__description) {
  color: var(--el-text-color-secondary);
}
</style>
