<script lang="ts" setup>
import { APanel, ATable, DialogUtil, FeedbackUtil } from '@airpower/web'

import { BillAuditOrReject, BillRejectDialog } from '@/component'
import { Permissions } from '@/config/permissions'
import { usePlanAudit, usePlanReject } from '@/hook/mes/usePlan'
import { useTanStackTable } from '@/hook/useTanStackTable'
import { PlanService } from '@/model/mes/plan/PlanService'
import { PlanStatusEnum } from '@/model/mes/plan/PlanStatusEnum'
import { PlanDetail, PlanEditor } from './component'

const hook = useTanStackTable(PlanService, {
  queryKey: ['mes:plan'],
  editView: PlanEditor,
  detailView: PlanDetail,
})

async function refreshList() {
  hook.onReloadData()
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
      :add-permission="Permissions.Mes.Plan.add"
      :detail-permission="Permissions.Mes.Plan.getDetail"
      :edit-permission="Permissions.Mes.Plan.update"
      :export-permission="Permissions.Mes.Plan.export"
      ctrl-width="160"
      hide-delete
      show-detail
      show-export
    >
      <template #customRow="{ data }">
        <BillAuditOrReject
          :audit-permission="Permissions.Mes.Plan.audit"
          :reject-permission="Permissions.Mes.Plan.reject"
          :bill="data"
          @on-audit="onAudit"
          @on-reject="onReject"
        />
      </template>
    </ATable>
  </APanel>
</template>

<style lang="scss" scoped></style>
