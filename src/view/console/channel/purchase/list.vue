<script lang="ts" setup>
import { APanel, ATable, DialogUtil, FeedbackUtil } from '@airpower/web'
import { BillAuditOrReject, BillRejectDialog } from '@/component'
import { Permissions } from '@/config/permissions'
import { PurchaseService, usePurchaseAudit, usePurchaseReject } from '@/hook/channel/usePurchase'
import { useTanStackTable } from '@/hook/useTanStackTable'
import { PurchaseStatusEnum } from '@/model/channel/purchase/PurchaseStatusEnum'
import { PurchaseDetail, PurchaseEditor } from './component'

const hook = useTanStackTable(PurchaseService, {
  queryKey: ['channel:purchase'],
  editView: PurchaseEditor,
  detailView: PurchaseDetail,
})

const { mutateAsync: auditMutate } = usePurchaseAudit()
async function onAudit(bill: { id: number }) {
  await FeedbackUtil.confirmWarning('是否确认审核选择的采购单？')
  await auditMutate({ id: bill.id })
  hook.onReloadData()
}

const { mutateAsync: rejectMutate } = usePurchaseReject()
async function onReject(bill: { id: number, rejectReason?: string }) {
  const rejectReason: string = await DialogUtil.show(BillRejectDialog, '驳回采购单的原因')
  await FeedbackUtil.confirmWarning('是否确认驳回选择的采购单？')
  await rejectMutate({ id: bill.id, rejectReason })
  hook.onReloadData()
}
</script>

<template>
  <APanel>
    <ATable
      :disable-edit="(row) => row.status !== PurchaseStatusEnum.REJECTED.key"
      :use-hook="hook"
      :add-permission="Permissions.Channel.Purchase.add"
      :detail-permission="Permissions.Channel.Purchase.getDetail"
      :edit-permission="Permissions.Channel.Purchase.update"
      ctrl-width="160"
      hide-delete
      show-detail
    >
      <template #customRow="{ data }">
        <BillAuditOrReject
          :bill="data"
          :audit-permission="Permissions.Channel.Purchase.audit"
          :reject-permission="Permissions.Channel.Purchase.reject"
          @on-audit="onAudit"
          @on-reject="onReject"
        />
      </template>
    </ATable>
  </APanel>
</template>

<style lang="scss" scoped></style>
