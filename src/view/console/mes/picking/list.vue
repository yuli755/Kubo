<script lang="ts" setup>
import { APanel, ATable, DialogUtil, FeedbackUtil } from '@airpower/web'
import { useQueryClient } from '@tanstack/vue-query'

import { BillAuditOrReject, BillRejectDialog } from '@/component'
import { useBillTable } from '@/hook/billTable/useBillTable'
import { usePickingAudit, usePickingReject } from '@/hook/mes/usePicking'
import { PickingService } from '@/model/mes/picking/PickingService'
import { PickingStatusEnum } from '@/model/mes/picking/PickingStatusEnum'
import { PickingDetail, PickingEditor } from './component'

const queryClient = useQueryClient()

const hook = useBillTable(PickingService, {
  editView: PickingEditor,
  detailView: PickingDetail,
})

async function refreshList() {
  hook.onReloadData()
  queryClient.invalidateQueries({ queryKey: ['mes:picking'] })
}

const { mutateAsync: auditMutate } = usePickingAudit()
async function onAudit(bill: { id: number }) {
  await FeedbackUtil.confirmWarning('是否确认审核选择的领料单？')
  await auditMutate({ id: bill.id })
  await refreshList()
}

const { mutateAsync: rejectMutate } = usePickingReject()
async function onReject(bill: { id: number, rejectReason?: string }) {
  const rejectReason: string = await DialogUtil.show(BillRejectDialog, '驳回领料单的原因')
  await FeedbackUtil.confirmWarning('是否确认驳回选择的领料单？')
  await rejectMutate({ id: bill.id, rejectReason })
  await refreshList()
}
</script>

<template>
  <APanel>
    <ATable
      :disable-edit="(row) => row.status !== PickingStatusEnum.REJECTED.key"
      :use-hook="hook"
      add-permission="Picking_add"
      detail-permission="Picking_getDetail"
      edit-permission="Picking_update"
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
