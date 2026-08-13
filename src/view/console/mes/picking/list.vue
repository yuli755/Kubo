<script lang="ts" setup>
import type { PickingQuery } from '@/api/mes/types'
import { APanel, ATable, DialogUtil, FeedbackUtil } from '@airpower/web'
import { computed, watch } from 'vue'

import { BillAuditOrReject, BillRejectDialog } from '@/component'
import { useBillTable } from '@/hook/billTable/useBillTable'
import { usePickingAudit, usePickingList, usePickingReject } from '@/hook/mes/usePicking'
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

const { data, isLoading } = usePickingList(query)

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

// ── Mutation: 审核 ──
const { mutateAsync: auditMutate } = usePickingAudit()
async function onAudit(bill: { id: number }) {
  await FeedbackUtil.confirmWarning('是否确认审核选择的领料单？')
  await auditMutate({ id: bill.id })
}

// ── Mutation: 驳回 ──
const { mutateAsync: rejectMutate } = usePickingReject()
async function onReject(bill: { id: number, rejectReason?: string }) {
  const rejectReason: string = await DialogUtil.show(BillRejectDialog, '驳回领料单的原因')
  await FeedbackUtil.confirmWarning('是否确认驳回选择的领料单？')
  await rejectMutate({ id: bill.id, rejectReason })
}
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
          audit-permission="Picking_audit"
          reject-permission="Picking_reject"
          @on-audit="onAudit"
          @on-reject="onReject"
        />
      </template>
    </ATable>
  </APanel>
</template>

<style lang="scss" scoped></style>
