<script lang="ts" setup>
import type { RoutingQuery } from '@/api/mes/types'
import type { RoutingEntity } from '@/model/mes/routing/RoutingEntity'
import { AButton, APanel, ATable, DialogUtil } from '@airpower/web'
import { computed, watch } from 'vue'
import PublishButton from '@/component/PublishButton.vue'
import { useRoutingList } from '@/hook/mes/useRouting'
import { useMyTable } from '@/hook/useMyTable'
import { RoutingService } from '@/model/mes/routing/RoutingService'
import { RoutingEditor, RoutingProgress } from './component'

const hook = useMyTable(RoutingService, {
  editView: RoutingEditor,
})

// 使用新 API Hook 替代 airpower service 的 getPage
// 透传 filter（code/name/isRoutingBom），保证搜索筛选条件到达新 API
const query = computed<RoutingQuery>(() => {
  const filter = hook.request.value.filter
  return {
    page: hook.request.value.page.pageNum || 1,
    pageSize: hook.request.value.page.pageSize || 20,
    filter: {
      ...(filter?.code ? { code: filter.code } : {}),
      ...(filter?.name ? { name: filter.name } : {}),
      ...(filter?.isRoutingBom !== undefined && filter?.isRoutingBom !== null
        ? { isRoutingBom: filter.isRoutingBom }
        : {}),
    },
  }
})

const { data: _routingData, isFetching, refetch } = useRoutingList(query)

// 覆写 service.getPage，使用 @tanstack/vue-query 替代 airpower 的 AbstractCurdService.getPage
hook.service.getPage = async () => {
  const result = await refetch()
  return { list: result.data?.list ?? [], total: result.data?.total ?? 0 }
}

// 同步加载状态到 hook，保持 ATable v-loading 正常工作
watch(isFetching, (v) => {
  hook.isLoading.value = v
})

async function onProgress(data: RoutingEntity) {
  await DialogUtil.show(RoutingProgress, data)
}
</script>

<template>
  <APanel>
    <ATable
      :disable-delete="(row) => row.isPublished"
      :disable-edit="(row) => row.isPublished"
      :use-hook="hook"
      ctrl-width="160"
    >
      <template #customRow="{ data }">
        <PublishButton
          :data
          @click="hook.onPublish"
        />
        <AButton
          link
          @click="onProgress(data)"
        >
          流程
        </AButton>
      </template>
    </ATable>
  </APanel>
</template>

<style lang="scss" scoped></style>
