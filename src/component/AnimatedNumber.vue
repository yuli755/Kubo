<script lang="ts" setup>
import { computed } from 'vue'
import { useCountUp } from '@/composables/useCountUp'

/**
 * # 数字滚动展示（V6 · 看板动效）
 *
 * 输入 `number | string`：纯数字走 `useCountUp` 滚动动画，非数字（如 `'3 / 5'`、`'—'`）原样显示。
 * 这样看板卡片里的「比率」「占位符」不会被迫塞进数字滚动。
 */

const props = defineProps<{
  value: number | string
}>()

const numeric = computed(() => (
  typeof props.value === 'number' && Number.isFinite(props.value) ? props.value : null
))

const { display } = useCountUp(() => numeric.value ?? 0)
</script>

<template>
  <span>{{ numeric === null ? props.value : display }}</span>
</template>
