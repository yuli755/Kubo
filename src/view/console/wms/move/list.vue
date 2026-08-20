<script lang="ts" setup>
import { APanel, ATable, DialogUtil, FeedbackUtil } from '@airpower/web'
import { BillAuditOrReject, BillRejectDialog } from '@/component'
import { useTanStackTable } from '@/hook/useTanStackTable'
import { useMoveAudit, useMoveReject } from '@/hook/wms/useMove'
import { MoveService } from '@/model/wms/move/MoveService'
import { MoveStatusEnum } from '@/model/wms/move/MoveStatusEnum'
import { Permissions } from '@/config/permissions'
import { MoveDetail, MoveEditor } from './component'

const hook = useTanStackTable(MoveService, {
  queryKey: ['wms:move'],
  editView: MoveEditor,
  detailView: MoveDetail,
})

async function refreshList() {
  hook.onReloadData()
}

const { mutateAsync: auditMutate } = useMoveAudit()
async function onAudit(bill: { id: number }) {
  await FeedbackUtil.confirmWarning('是否确认审核选择的移库单？')
  await auditMutate({ id: bill.id })
  await refreshList()
}

const { mutateAsync: rejectMutate } = useMoveReject()
async function onReject(bill: { id: number, rejectReason?: string }) {
  const rejectReason: string = await DialogUtil.show(BillRejectDialog, '驳回移库单的原因')
  await FeedbackUtil.confirmWarning('是否确认驳回选择的移库单？')
  await rejectMutate({ id: bill.id, rejectReason })
  await refreshList()
}
</script>

<template>
  <APanel>
    <ATable
      :disable-edit="(row) => !MoveStatusEnum.REJECTED.equalsKey(row.status)"
      :use-hook="hook"
      :add-permission="Permissions.Wms.Move.add"
      :detail-permission="Permissions.Wms.Move.getDetail"
      :edit-permission="Permissions.Wms.Move.update"
      ctrl-width="160"
      hide-delete
      show-detail
    >
      <template #customRow="{ data }">
        <BillAuditOrReject
          :audit-permission="Permissions.Wms.Move.audit"
          :reject-permission="Permissions.Wms.Move.reject"
          :bill="data"
          @on-audit="onAudit"
          @on-reject="onReject"
        />
      </template>
    </ATable>
  </APanel>
</template>

<style lang="scss" scoped></style>
