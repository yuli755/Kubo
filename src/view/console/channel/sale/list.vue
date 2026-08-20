<script lang="ts" setup>
import { APanel, ATable, DialogUtil, FeedbackUtil } from '@airpower/web'
import { BillAuditOrReject, BillRejectDialog } from '@/component'
import { useBillTable } from '@/hook/billTable/useBillTable'
import { useSaleAudit, useSaleReject } from '@/hook/channel/useSale'
import { SaleService } from '@/model/channel/sale/SaleService'
import { SaleStatusEnum } from '@/model/channel/sale/SaleStatusEnum'
import { SaleDetail, SaleEditor } from './component'

const hook = useBillTable(SaleService, {
  editView: SaleEditor,
  detailView: SaleDetail,
})

const { mutateAsync: auditMutate } = useSaleAudit()
async function onAudit(bill: { id: number }) {
  await FeedbackUtil.confirmWarning('是否确认审核选择的销售单？')
  await auditMutate({ id: bill.id })
  hook.onReloadData()
}

const { mutateAsync: rejectMutate } = useSaleReject()
async function onReject(bill: { id: number, rejectReason?: string }) {
  const rejectReason: string = await DialogUtil.show(BillRejectDialog, '驳回销售单的原因')
  await FeedbackUtil.confirmWarning('是否确认驳回选择的销售单？')
  await rejectMutate({ id: bill.id, rejectReason })
  hook.onReloadData()
}
</script>

<template>
  <APanel>
    <ATable
      :disable-edit="(row) => !SaleStatusEnum.REJECTED.equalsKey(row.status)"
      :use-hook="hook"
      add-permission="Sale_add"
      detail-permission="Sale_getDetail"
      edit-permission="Sale_update"
      ctrl-width="160"
      hide-delete
      show-detail
    >
      <template #customRow="{ data }">
        <BillAuditOrReject
          :bill="data"
          audit-permission="Sale_audit"
          reject-permission="Sale_reject"
          @on-audit="onAudit"
          @on-reject="onReject"
        />
      </template>
    </ATable>
  </APanel>
</template>

<style lang="scss" scoped></style>
