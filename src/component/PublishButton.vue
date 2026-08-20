<script generic="E extends BaseEntity" lang="ts" setup>
import type { BaseEntity } from '@/base/BaseEntity'
import { AButton } from '@airpower/web'
import { vPermission } from '@/directives'

const { data, publishPermission } = defineProps<{
  /**
   * ### 实体
   */
  data: E
  /**
   * ### 发布权限码
   *
   * 传入则按权限控制「发布」按钮显隐；不传则始终展示。
   */
  publishPermission?: string
}>()

const emits = defineEmits(['click'])
</script>

<template>
  <AButton
    v-permission="publishPermission"
    :disabled="data.isPublished"
    link
    @click="emits('click', data)"
  >
    发布
  </AButton>
</template>

<style lang="scss" scoped></style>
