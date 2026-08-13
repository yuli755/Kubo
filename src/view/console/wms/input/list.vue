<script lang="ts" setup>
import { APanel, ATable } from '@airpower/web'
import { computed, watch } from 'vue'
import { BillAuditOrReject } from '@/component'
import { useBillTable } from '@/hook/billTable/useBillTable'
import { useInputList } from '@/hook/wms/useInput'
import { InputService } from '@/model/wms/input/InputService'
import { InputStatusEnum } from '@/model/wms/input/InputStatusEnum'
import { InputDetail, InputEditor } from './component'

const hook = useBillTable(InputService, {
  editView: InputEditor,
  detailView: InputDetail,
})

// 使用新 API Hook 替代 airpower service 的 getPage
// 透传 filter（billCode/type/status），保证搜索筛选条件到达新 API
const query = computed(() => {
  const filter = hook.request.value.filter
  return {
    page: hook.request.value.page.pageNum || 1,
    pageSize: hook.request.value.page.pageSize || 20,
    filter: {
      ...(filter?.billCode ? { billCode: filter.billCode } : {}),
      ...(filter?.type !== undefined && filter?.type !== null ? { type: filter.type } : {}),
      ...(filter?.status !== undefined && filter?.status !== null ? { status: filter.status } : {}),
    },
  }
})

const { data: _inputData, isFetching, refetch } = useInputList(query)

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
      :disable-edit="(row) => !InputStatusEnum.REJECTED.equalsKey(row.status)"
      :use-hook="hook"
      ctrl-width="200"
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
