<script lang="ts" setup>
import { APanel, ATable, DialogUtil, FeedbackUtil } from '@airpower/web'
import { useQueryClient } from '@tanstack/vue-query'

import { BillAuditOrReject, BillRejectDialog } from '@/component'
import { useBillTable } from '@/hook/billTable/useBillTable'
import { usePlanAudit, usePlanReject } from '@/hook/mes/usePlan'
import { PlanService } from '@/model/mes/plan/PlanService'
import { PlanStatusEnum } from '@/model/mes/plan/PlanStatusEnum'
import { PlanDetail, PlanEditor } from './component'

const queryClient = useQueryClient()

const hook = useBillTable(PlanService, {
  editView: PlanEditor,
  detailView: PlanDetail,
})

async function refreshList() {
  hook.onReloadData()
  queryClient.invalidateQueries({ queryKey: ['mes:plan'] })
}

const { mutateAsync: auditMutate } = usePlanAudit()
async function onAudit(bill: { id: number }) {
  await FeedbackUtil.confirmWarning('是否确认审核选择的生产计划？')
  await auditMutate({ id: bill.id })
  await refreshList()
}

const { mutateAsync: rejectMutate } = usePlanReject()
async function onReject(bill: { id: number, rejectReason?: string }) {
  const rejectReason: string = await DialogUtil.show(BillRejectDialog, '驳回生产计划的原因')
  await FeedbackUtil.confirmWarning('是否确认驳回选择的生产计划？')
  await rejectMutate({ id: bill.id, rejectReason })
  await refreshList()
}
</script>

<template>
  <APanel>
    <ATable
      :disable-edit="(row) => !PlanStatusEnum.REJECTED.equalsKey(row.status)"
      :use-hook="hook"
      add-permission="Plan_add"
      detail-permission="Plan_getDetail"
      edit-permission="Plan_update"
      ctrl-width="160"
      hide-delete
      show-detail
    >
      <template #customRow="{ data }">
        <BillAuditOrReject
          audit-permission="Plan_audit"
          reject-permission="Plan_reject"
          :bill="data"
          @on-audit="onAudit"
          @on-reject="onReject"
        />
      </template>
    </ATable>
  </APanel>
</template>

<style lang="scss" scoped></style>
