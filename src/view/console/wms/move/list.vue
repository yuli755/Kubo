<script lang="ts" setup>
import { APanel, ATable } from '@airpower/web'
import { computed, watch } from 'vue'
import { BillAuditOrReject } from '@/component'
import { useBillTable } from '@/hook/billTable/useBillTable'
import { useMoveList } from '@/hook/wms/useMove'
import { MoveService } from '@/model/wms/move/MoveService'
import { MoveStatusEnum } from '@/model/wms/move/MoveStatusEnum'
import { MoveDetail, MoveEditor } from './component'

const hook = useBillTable(MoveService, {
  editView: MoveEditor,
  detailView: MoveDetail,
})

// 使用新 API Hook 替代 airpower service 的 getPage
// 透传 filter（billCode/status），保证搜索筛选条件到达新 API
const query = computed(() => {
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

const { data: _moveData, isFetching, refetch } = useMoveList(query)

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
      :disable-edit="(row) => !MoveStatusEnum.REJECTED.equalsKey(row.status)"
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
