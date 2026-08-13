<script lang="ts" setup>
import { APanel, ATable, DialogUtil, FeedbackUtil } from '@airpower/web'
import { computed, watch } from 'vue'
import { BillAuditOrReject, BillRejectDialog } from '@/component'
import { useBillTable } from '@/hook/billTable/useBillTable'
import { useMoveAudit, useMoveList, useMoveReject } from '@/hook/wms/useMove'
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

const { data, isLoading } = useMoveList(query)

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
const { mutateAsync: auditMutate } = useMoveAudit()
async function onAudit(bill: { id: number }) {
  await FeedbackUtil.confirmWarning('是否确认审核选择的移库单？')
  await auditMutate({ id: bill.id })
}

// ── Mutation: 驳回 ──
const { mutateAsync: rejectMutate } = useMoveReject()
async function onReject(bill: { id: number, rejectReason?: string }) {
  const rejectReason: string = await DialogUtil.show(BillRejectDialog, '驳回移库单的原因')
  await FeedbackUtil.confirmWarning('是否确认驳回选择的移库单？')
  await rejectMutate({ id: bill.id, rejectReason })
}
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
          @on-audit="onAudit"
          @on-reject="onReject"
        />
      </template>
    </ATable>
  </APanel>
</template>

<style lang="scss" scoped></style>
