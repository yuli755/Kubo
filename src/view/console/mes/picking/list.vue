<script lang="ts" setup>
import { APanel, ATable, DialogUtil, FeedbackUtil } from '@airpower/web'

import { BillAuditOrReject, BillRejectDialog } from '@/component'
import { Permissions } from '@/config/permissions'
import { usePickingAudit, usePickingReject } from '@/hook/mes/usePicking'
import { useTanStackTable } from '@/hook/useTanStackTable'
import { PickingService } from '@/model/mes/picking/PickingService'
import { PickingStatusEnum } from '@/model/mes/picking/PickingStatusEnum'
import { PickingDetail, PickingEditor } from './component'

const hook = useTanStackTable(PickingService, {
  queryKey: ['mes:picking'],
  editView: PickingEditor,
  detailView: PickingDetail,
})

async function refreshList() {
  hook.onReloadData()
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
      :add-permission="Permissions.Mes.Picking.add"
      :detail-permission="Permissions.Mes.Picking.getDetail"
      :edit-permission="Permissions.Mes.Picking.update"
      :export-permission="Permissions.Mes.Picking.export"
      ctrl-width="160"
      hide-delete
      show-detail
      show-export
    >
      <template #customRow="{ data }">
        <BillAuditOrReject
          :bill="data"
          :audit-permission="Permissions.Mes.Picking.audit"
          :reject-permission="Permissions.Mes.Picking.reject"
          @on-audit="onAudit"
          @on-reject="onReject"
        />
      </template>
    </ATable>
  </APanel>
</template>

<style lang="scss" scoped></style>
