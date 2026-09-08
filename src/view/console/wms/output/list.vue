<script lang="ts" setup>
import { APanel, ATable, DialogUtil, FeedbackUtil } from '@airpower/web'
import { BillAuditOrReject, BillRejectDialog } from '@/component'
import { useTanStackTable } from '@/hook/useTanStackTable'
import { OutputService, useOutputAudit, useOutputReject } from '@/hook/wms/useOutput'
import { OutputStatusEnum } from '@/model/wms/output/OutputStatusEnum'
import { Permissions } from '@/config/permissions'
import { OutputDetail, OutputEditor } from '@/view/console/wms/output/component'

const hook = useTanStackTable(OutputService, {
  queryKey: ['wms:output'],
  editView: OutputEditor,
  detailView: OutputDetail,
})

async function refreshList() {
  hook.onReloadData()
}

const { mutateAsync: auditMutate } = useOutputAudit()
async function onAudit(bill: { id: number }) {
  await FeedbackUtil.confirmWarning('是否确认审核选择的出库单？')
  await auditMutate({ id: bill.id })
  await refreshList()
}

const { mutateAsync: rejectMutate } = useOutputReject()
async function onReject(bill: { id: number; rejectReason?: string }) {
  const rejectReason: string = await DialogUtil.show(BillRejectDialog, '驳回出库单的原因')
  await FeedbackUtil.confirmWarning('是否确认驳回选择的出库单？')
  await rejectMutate({ id: bill.id, rejectReason })
  await refreshList()
}
</script>

<template>
  <APanel>
    <ATable
      :disable-edit="(row) => !OutputStatusEnum.REJECTED.equalsKey(row.status)"
      :use-hook="hook"
      :add-permission="Permissions.Wms.Output.add"
      :detail-permission="Permissions.Wms.Output.getDetail"
      :edit-permission="Permissions.Wms.Output.update"
      :export-permission="Permissions.Wms.Output.export"
      ctrl-width="160"
      hide-delete
      show-detail
      show-export
    >
      <template #customRow="{ data }">
        <BillAuditOrReject
          :audit-permission="Permissions.Wms.Output.audit"
          :reject-permission="Permissions.Wms.Output.reject"
          :bill="data"
          @on-audit="onAudit"
          @on-reject="onReject"
        />
      </template>
    </ATable>
  </APanel>
</template>

<style lang="scss" scoped></style>
