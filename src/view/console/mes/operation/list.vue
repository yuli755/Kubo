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

const { data: _operationData, isFetching, refetch } = useOperationList(query)

// 覆写 service.getPage，使用 @tanstack/vue-query 替代 airpower 的 AbstractCurdService.getPage
hook.service.getPage = async () => {
  const result = await refetch()
  return { list: result.data?.list ?? [], total: result.data?.total ?? 0 }
}

// 同步加载状态到 hook，保持 ATable v-loading 正常工作
watch(isFetching, (v) => {
  hook.isLoading.value = v
})
</script>

<template>
  <APanel>
    <ATable :use-hook="hook" />
  </APanel>
</template>

<style lang="scss" scoped></style>
