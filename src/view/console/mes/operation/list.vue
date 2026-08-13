<script lang="ts" setup>
import type { OperationQuery } from '@/api/mes/types'
import { APanel, ATable } from '@airpower/web'
import { computed, watch } from 'vue'
import { useOperationList } from '@/hook/mes/useOperation'
import { useMyTable } from '@/hook/useMyTable'
import { OperationService } from '@/model/mes/operation/OperationService'
import { OperationEditor } from './component'

const hook = useMyTable(OperationService, {
  editView: OperationEditor,
})

// 使用新 API Hook 替代 airpower service 的 getPage
// 透传 filter（code/name），保证搜索筛选条件到达新 API
const query = computed<OperationQuery>(() => {
  const filter = hook.request.value.filter
  return {
    page: hook.request.value.page.pageNum || 1,
    pageSize: hook.request.value.page.pageSize || 20,
    filter: {
      ...(filter?.code ? { code: filter.code } : {}),
      ...(filter?.name ? { name: filter.name } : {}),
    },
  }
})

const { data, isLoading } = useOperationList(query)

// 将新 API 响应同步到 airpower QueryResponsePage 格式，保持 ATable 兼容
watch(data, (value) => {
  if (value) {
    hook.response.value.list = value.list
    hook.response.value.total = value.total
  }
})

// 同步加载状态到 hook，保持 ATable v-loading 正常工作
watch(isLoading, (v) => {
  hook.isLoading.value = v
})
</script>

<template>
  <APanel>
    <ATable :use-hook="hook" />
  </APanel>
</template>

<style lang="scss" scoped></style>
