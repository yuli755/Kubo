<script lang="ts" setup>
/**
 * # 通用骨架屏（V5 · 加载态分级）
 *
 * 用于「首次加载」阶段的占位，替代整页 loading 遮罩。只做视觉占位，不承载任何数据逻辑；
 * shimmer 高光走 `--kubo-skeleton-highlight`（浅色/深色各自定义），底色走
 * `--kubo-color-muted-bg`，因此自动适配 V1/V2 的主题层。
 *
 * ## 形态（variant）
 * - `card`：卡片块（标题条 + 大数字块），配合 `cols` 排成网格；
 * - `chart`：图表占位大块；
 * - `table`：表头 + 行占位；
 * - `text`：宽度递减的文本行（默认）。
 *
 * ## 无障碍
 * 骨架屏是纯装饰，标记 `aria-hidden`；shimmer 动画在 `prefers-reduced-motion` 下关闭（V8）。
 */

interface Props {
  /** 骨架形态 */
  variant?: 'text' | 'card' | 'chart' | 'table'
  /** 骨架块数量 */
  count?: number
  /** 表格占位行数（仅 table 形态生效） */
  rows?: number
}

const props = withDefaults(defineProps<Props>(), {
  variant: 'text',
  count: 1,
  rows: 3,
})
</script>

<template>
  <div
    v-if="props.variant === 'card'"
    aria-hidden="true"
    class="skeleton"
  >
    <div
      v-for="i in props.count"
      :key="i"
      class="skeleton__item skeleton__item--card"
    >
      <div class="skeleton__line skeleton__line--label" />
      <div class="skeleton__line skeleton__line--value" />
    </div>
  </div>

  <div
    v-else-if="props.variant === 'chart'"
    aria-hidden="true"
    class="skeleton"
  >
    <div class="skeleton__item skeleton__item--chart" />
  </div>

  <div
    v-else-if="props.variant === 'table'"
    aria-hidden="true"
    class="skeleton skeleton--table"
  >
    <div class="skeleton__item skeleton__item--header" />
    <div
      v-for="i in props.rows"
      :key="i"
      class="skeleton__item skeleton__item--row"
    />
  </div>

  <div
    v-else
    aria-hidden="true"
    class="skeleton skeleton--text"
  >
    <div
      v-for="i in props.count"
      :key="i"
      class="skeleton__item skeleton__item--text"
      :style="{ width: `${100 - (i - 1) * 18}%` }"
    />
  </div>
</template>

<style lang="scss" scoped>
.skeleton {
  display: flex;
  flex-direction: column;
  gap: var(--kubo-spacing-3);
  width: 100%;

  /* card / chart 形态：网格排列，按容器宽度自适应列数（无需硬编码断点） */
  &:not(.skeleton--text):not(.skeleton--table) {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  }

  &--table {
    display: flex;
    flex-direction: column;
  }
}

.skeleton__item {
  position: relative;
  overflow: hidden;
  background: var(--kubo-color-muted-bg);
  border-radius: var(--kubo-radius-sm);

  /* shimmer 高光：只做 transform 位移，合成器友好 */
  &::after {
    content: '';
    position: absolute;
    inset: 0;
    transform: translateX(-100%);
    background: linear-gradient(90deg, transparent, var(--kubo-skeleton-highlight), transparent);
    animation: kubo-skeleton-shimmer 1.4s var(--kubo-ease-in-out) infinite;
  }

  &--card {
    height: 88px;
    padding: var(--kubo-spacing-4);
    display: flex;
    flex-direction: column;
    justify-content: space-between;
  }

  &--chart {
    height: 260px;
  }

  &--header {
    height: 40px;
  }

  &--row {
    height: 24px;
  }

  &--text {
    height: 14px;
  }
}

.skeleton__line {
  background: var(--kubo-color-muted-border);
  border-radius: var(--kubo-radius-sm);

  &--label {
    width: 46%;
    height: 12px;
  }

  &--value {
    width: 64%;
    height: 22px;
  }
}

@keyframes kubo-skeleton-shimmer {
  100% {
    transform: translateX(100%);
  }
}

/* V8：用户偏好减少动效时关闭 shimmer，只保留静态占位 */
@media (prefers-reduced-motion: reduce) {
  .skeleton__item::after {
    animation: none;
  }
}
</style>
