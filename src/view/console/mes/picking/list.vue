<script lang="ts" setup>
import type { PickingQuery } from '@/api/mes/types'
import { APanel, ATable } from '@airpower/web'
import { computed, watch } from 'vue'

import { BillAuditOrReject } from '@/component'
import { useBillTable } from '@/hook/billTable/useBillTable'
import { usePickingList } from '@/hook/mes/usePicking'
import { PickingService } from '@/model/mes/picking/PickingService'
import { PickingStatusEnum } from '@/model/mes/picking/PickingStatusEnum'
import { PickingDetail, PickingEditor } from './component'

const hook = useBillTable(PickingService, {
  editView: PickingEditor,
  detailView: PickingDetail,
})

// 使用新 API Hook 替代 airpower service 的 getPage
// 透传 filter（billCode/status），保证搜索筛选条件到达新 API
const query = computed<PickingQuery>(() => {
  const filter = hook.request.value.filter
  return {
    page: hook.request.value.page.pageNum || 1,
    pageSize: hook.request.value.page.pageSize || 20,
    filter: {
      ...(filter?.billCode ? { billCode: filter.billCode } : {}),
      ...(filter?.status !== undefined && filter?.status !== null ? { status: filter.status } : {}),
    },
  }
})

const { data: _pickingData, isFetching, refetch } = usePickingList(query)

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
      :disable-edit="(row) => row.status !== PickingStatusEnum.REJECTED.key"
      :use-hook="hook"
      ctrl-width="160"
      hide-delete
      show-detail
    >
      <template #customRow="{ data }">
        <BillAuditOrReject
          :bill="data"
          @on-audit="hook.onAudit"
          @on-reject="hook.onReject"
        />
      </template>
    </ATable>
  </APanel>
</template>

<style lang="scss" scoped></style>
