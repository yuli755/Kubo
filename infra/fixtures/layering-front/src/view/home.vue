<script setup lang="ts">
import { internal } from '@/framework/internal'

/** 视图是叶子：可以依赖任意下层 */
const ready = internal
</script>

<template>
  <div>{{ ready }}</div>
</template>
