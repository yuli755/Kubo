<script lang="ts" setup>
import type { RoutingQuery } from '@/api/mes/types'
import type { RoutingEntity } from '@/model/mes/routing/RoutingEntity'
import { AButton, APanel, ATable, DialogUtil, FeedbackUtil } from '@airpower/web'
import { computed, watch } from 'vue'
import PublishButton from '@/component/PublishButton.vue'
import { useRoutingList, useRoutingPublish } from '@/hook/mes/useRouting'
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

const { data, isLoading } = useRoutingList(query)

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

// ── Mutation: 发布 ──
const { mutateAsync: publishMutate } = useRoutingPublish()
async function onPublish(entity: { id: number }) {
  await FeedbackUtil.confirmWarning('发布后将无法再次修改，是否确认？', '确认发布')
  await publishMutate({ id: entity.id })
}

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
          @click="onPublish"
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
