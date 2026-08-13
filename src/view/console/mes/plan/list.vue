<script lang="ts" setup>
import type { PlanQuery } from '@/api/mes/types'
import { APanel, ATable, DialogUtil, FeedbackUtil } from '@airpower/web'
import { computed, watch } from 'vue'

import { BillAuditOrReject, BillRejectDialog } from '@/component'
import { useBillTable } from '@/hook/billTable/useBillTable'
import { usePlanAudit, usePlanList, usePlanReject } from '@/hook/mes/usePlan'
import { PlanService } from '@/model/mes/plan/PlanService'
import { PlanStatusEnum } from '@/model/mes/plan/PlanStatusEnum'
import { PlanDetail, PlanEditor } from './component'

const hook = useBillTable(PlanService, {
  editView: PlanEditor,
  detailView: PlanDetail,
})

// 使用新 API Hook 替代 airpower service 的 getPage
// 透传 filter（billCode/type/status），保证搜索筛选条件到达新 API
const query = computed<PlanQuery>(() => {
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

const { data, isLoading } = usePlanList(query)

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
const { mutateAsync: auditMutate } = usePlanAudit()
async function onAudit(bill: { id: number }) {
  await FeedbackUtil.confirmWarning('是否确认审核选择的生产计划？')
  await auditMutate({ id: bill.id })
}

// ── Mutation: 驳回 ──
const { mutateAsync: rejectMutate } = usePlanReject()
async function onReject(bill: { id: number, rejectReason?: string }) {
  const rejectReason: string = await DialogUtil.show(BillRejectDialog, '驳回生产计划的原因')
  await FeedbackUtil.confirmWarning('是否确认驳回选择的生产计划？')
  await rejectMutate({ id: bill.id, rejectReason })
}
</script>

<template>
  <APanel>
    <ATable
      :disable-edit="(row) => !PlanStatusEnum.REJECTED.equalsKey(row.status)"
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
