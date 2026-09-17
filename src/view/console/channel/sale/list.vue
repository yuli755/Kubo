<script lang="ts" setup>
import type { SaleEntity } from '@/model/channel/sale/SaleEntity'
import { ref } from 'vue'
import BillRejectDialog from '@/component/bill/BillRejectDialog.vue'
import KPanel from '@/component/framework/KPanel.vue'
import KTable from '@/component/framework/KTable.vue'
import { Permissions } from '@/config/permissions'
import { DialogUtil, FeedbackUtil } from '@/framework'
import { KSaleService } from '@/hook/channel/useSale'
import { useTable } from '@/hook/kubo/useTable'
import { SaleStatusEnum } from '@/model/channel/sale/SaleStatusEnum'
import SaleDetail from './component/detail.vue'
import SaleEditor from './component/editor.vue'

/**
 * # 销售单列表（F4 批 5 · 新底座）
 *
 * 与采购单列表同构（状态机判据 + `hide-delete` / `hide-enable` + 命令式「查看」）；
 * 状态字典换成 `SaleStatusEnum`，文案换成「销售单」。
 *
 * 迁移时把两处复制粘贴留下的旧文案修了：
 * - 编辑器分组标题「采购单」→「销售单」；
 * - 删明细确认「采购明细」→「销售明细」。
 */
const table = useTable(KSaleService, {
  queryKey: ['channel:sale'],
})

const editorVisible = ref(false)
const editingId = ref<number | null>(null)

const rejecting = ref(false)
const rejectingBill = ref<SaleEntity | null>(null)

function openCreate() {
  editingId.value = null
  editorVisible.value = true
}

function openEdit(row: SaleEntity) {
  editingId.value = row.id
  editorVisible.value = true
}

function openDetail(row: SaleEntity) {
  void DialogUtil.show(SaleDetail, row)
}

async function onAudit(row: SaleEntity) {
  await FeedbackUtil.confirmWarning('是否确认审核选择的销售单？', '审核销售单')
  await new KSaleService().audit(row)
  FeedbackUtil.toastSuccess('审核成功')
  table.reload()
}

function onRejectStart(row: SaleEntity) {
  rejectingBill.value = row
  rejecting.value = true
}

async function onRejectConfirm(reason: string) {
  const bill = rejectingBill.value
  rejecting.value = false
  rejectingBill.value = null
  if (!bill) {
    return
  }
  await FeedbackUtil.confirmWarning('是否确认驳回选择的销售单？', '驳回销售单')
  await new KSaleService().reject(bill, reason)
  FeedbackUtil.toastSuccess('驳回成功')
  table.reload()
}

function onSaved() {
  table.reload()
}
</script>

<template>
  <div class="k-page">
    <KPanel
      title="销售单"
      description="销售出库来源单据：审核后由出库单逐条「作业」登记实际出库数量"
    >
      <KTable
        :actions="[
          { label: '查看', permission: Permissions.Channel.Sale.getDetail, handler: openDetail },
          { label: '审核', permission: Permissions.Channel.Sale.audit, disabled: (row: SaleEntity) => !row.canAudit(), handler: onAudit },
          { label: '驳回', permission: Permissions.Channel.Sale.reject, disabled: (row: SaleEntity) => !row.canReject(), handler: onRejectStart },
        ]"
        :disable-edit="(row: SaleEntity) => !SaleStatusEnum.REJECTED.equalsKey(row.status)"
        :permissions="{
          add: Permissions.Channel.Sale.add,
          edit: Permissions.Channel.Sale.update,
        }"
        :table="table"
        hide-delete
        hide-enable
        @add="openCreate"
        @edit="openEdit"
      />
    </KPanel>

    <SaleEditor
      :id="editingId"
      v-model="editorVisible"
      @saved="onSaved"
    />

    <BillRejectDialog
      v-if="rejecting"
      :on-cancel="() => { rejecting = false; rejectingBill = null }"
      :on-confirm="(data) => onRejectConfirm(String(data ?? ''))"
      param="驳回销售单的原因"
    />
  </div>
</template>

<style lang="scss" scoped>
.k-page {
  padding: 16px;
}
</style>
