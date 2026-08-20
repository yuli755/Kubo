<script lang="ts" setup>
import { APanel, ATable, DialogUtil, FeedbackUtil } from '@airpower/web'
import { useQueryClient } from '@tanstack/vue-query'
import { BillAuditOrReject, BillRejectDialog } from '@/component'
import { useBillTable } from '@/hook/billTable/useBillTable'
import { useInputAudit, useInputReject } from '@/hook/wms/useInput'
import { InputService } from '@/model/wms/input/InputService'
import { InputStatusEnum } from '@/model/wms/input/InputStatusEnum'
import { Permissions } from '@/config/permissions'
import { InputDetail, InputEditor } from './component'

const queryClient = useQueryClient()

const hook = useBillTable(InputService, {
  editView: InputEditor,
  detailView: InputDetail,
})

async function refreshList() {
  hook.onReloadData()
  queryClient.invalidateQueries({ queryKey: ['wms:input'] })
}

const { mutateAsync: auditMutate } = useInputAudit()
async function onAudit(bill: { id: number }) {
  await FeedbackUtil.confirmWarning('是否确认审核选择的入库单？')
  await auditMutate({ id: bill.id })
  await refreshList()
}

const { mutateAsync: rejectMutate } = useInputReject()
async function onReject(bill: { id: number, rejectReason?: string }) {
  const rejectReason: string = await DialogUtil.show(BillRejectDialog, '驳回入库单的原因')
  await FeedbackUtil.confirmWarning('是否确认驳回选择的入库单？')
  await rejectMutate({ id: bill.id, rejectReason })
  await refreshList()
}
</script>

<template>
  <APanel>
    <ATable
      :disable-edit="(row) => !InputStatusEnum.REJECTED.equalsKey(row.status)"
      :use-hook="hook"
      :add-permission="Permissions.Wms.Input.add"
      :detail-permission="Permissions.Wms.Input.getDetail"
      :edit-permission="Permissions.Wms.Input.update"
      ctrl-width="200"
      hide-delete
      show-detail
    >
      <template #customRow="{ data }">
        <BillAuditOrReject
          :audit-permission="Permissions.Wms.Input.audit"
          :reject-permission="Permissions.Wms.Input.reject"
          :bill="data"
          @on-audit="onAudit"
          @on-reject="onReject"
        />
      </template>
    </ATable>
  </APanel>
</template>

<style lang="scss" scoped></style>
