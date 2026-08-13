<script lang="ts" setup>
import type { BomQuery } from '@/api/mes/types'
import { APanel, ATable } from '@airpower/web'
import { computed, watch } from 'vue'
import PublishButton from '@/component/PublishButton.vue'
import { useBomList } from '@/hook/mes/useBom'
import { useMyTable } from '@/hook/useMyTable'
import { BomService } from '@/model/mes/bom/BomService'
import { BomDetail, BomEditor } from './component'

const hook = useMyTable(BomService, {
  editView: BomEditor,
  detailView: BomDetail,
})

// 使用新 API Hook 替代 airpower service 的 getPage
// 透传 filter（code/name/type），保证搜索筛选条件到达新 API
const query = computed<BomQuery>(() => {
  const filter = hook.request.value.filter
  return {
    page: hook.request.value.page.pageNum || 1,
    pageSize: hook.request.value.page.pageSize || 20,
    filter: {
      ...(filter?.code ? { code: filter.code } : {}),
      ...(filter?.name ? { name: filter.name } : {}),
      ...(filter?.type !== undefined && filter?.type !== null ? { type: filter.type } : {}),
    },
  }
})

const { data: _bomData, isFetching, refetch } = useBomList(query)

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
    <ATable
      :disable-delete="(row) => row.isPublished"
      :disable-edit="(row) => row.isPublished"
      :use-hook="hook"
      ctrl-width="160"
      show-detail
    >
      <template #customRow="{ data }">
        <PublishButton
          :data
          @click="hook.onPublish"
        />
      </template>
    </ATable>
  </APanel>
</template>

<style lang="scss" scoped></style>
