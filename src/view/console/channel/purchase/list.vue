<script lang="ts" setup>
import type { PurchaseEntity } from '@/model/channel/purchase/PurchaseEntity'
import { ref } from 'vue'
import BillRejectDialog from '@/component/bill/BillRejectDialog.vue'
import KPanel from '@/component/framework/KPanel.vue'
import KTable from '@/component/framework/KTable.vue'
import { Permissions } from '@/config/permissions'
import { DialogUtil, FeedbackUtil } from '@/framework'
import { KPurchaseService } from '@/hook/channel/usePurchase'
import { useTable } from '@/hook/kubo/useTable'
import { PurchaseStatusEnum } from '@/model/channel/purchase/PurchaseStatusEnum'
import PurchaseDetail from './component/detail.vue'
import PurchaseEditor from './component/editor.vue'

/**
 * # 采购单列表（F4 批 5 · 新底座）
 *
 * 单据列表与 wms 入库/出库同构，只多两处 channel 特有的口径：
 *
 * - **审核 / 驳回用实体自己的状态机**：`canAudit()` / `canReject()` 来自
 *   `AbstractBaseBillEntity`，页面不重算状态；
 * - **只有「已驳回」的单据可编辑**：`disable-edit` 判据与旧页面完全一致
 *   （`status !== REJECTED` 时置灰），审核中/采购中的单据不许改。
 *
 * 「采购单没有删除端点也没有启停语义」：后端 `PurchaseController` 上写着
 * `exclude = Curd.Delete`，`@Extends` 也没有加回 Enable/Disable ——
 * 而 `v-permission` 遇到未传的权限码是不拦截的，所以 `hide-delete` / `hide-enable`
 * 必须显式给，否则用户会看到点下去必然报错的按钮。
 *
 * 「查看」保持命令式打开（`DialogUtil.show`）：`channel/purchase/component/openDetail.ts`
 * 这个跨域接缝也走同一个组件，wms 入库详情点「采购单号」时用的就是它。
 */
const table = useTable(KPurchaseService, {
  queryKey: ['channel:purchase'],
})

const editorVisible = ref(false)
const editingId = ref<number | null>(null)

const rejecting = ref(false)
const rejectingBill = ref<PurchaseEntity | null>(null)

function openCreate() {
  editingId.value = null
  editorVisible.value = true
}

function openEdit(row: PurchaseEntity) {
  editingId.value = row.id
  editorVisible.value = true
}

function openDetail(row: PurchaseEntity) {
  void DialogUtil.show(PurchaseDetail, row)
}

async function onAudit(row: PurchaseEntity) {
  await FeedbackUtil.confirmWarning('是否确认审核选择的采购单？', '审核采购单')
  await new KPurchaseService().audit(row)
  FeedbackUtil.toastSuccess('审核成功')
  table.reload()
}

function onRejectStart(row: PurchaseEntity) {
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
  await FeedbackUtil.confirmWarning('是否确认驳回选择的采购单？', '驳回采购单')
  await new KPurchaseService().reject(bill, reason)
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
      title="采购单"
      description="采购申请 → 审核 → 逐条「作业」登记到货数量；审核中的单据不可编辑"
    >
      <KTable
        :actions="[
          { label: '查看', permission: Permissions.Channel.Purchase.getDetail, handler: openDetail },
          { label: '审核', permission: Permissions.Channel.Purchase.audit, disabled: (row: PurchaseEntity) => !row.canAudit(), handler: onAudit },
          { label: '驳回', permission: Permissions.Channel.Purchase.reject, disabled: (row: PurchaseEntity) => !row.canReject(), handler: onRejectStart },
        ]"
        :disable-edit="(row: PurchaseEntity) => !PurchaseStatusEnum.REJECTED.equalsKey(row.status)"
        :permissions="{
          add: Permissions.Channel.Purchase.add,
          edit: Permissions.Channel.Purchase.update,
        }"
        :table="table"
        hide-delete
        hide-enable
        @add="openCreate"
        @edit="openEdit"
      />
    </KPanel>

    <PurchaseEditor
      :id="editingId"
      v-model="editorVisible"
      @saved="onSaved"
    />

    <BillRejectDialog
      v-if="rejecting"
      :on-cancel="() => { rejecting = false; rejectingBill = null }"
      :on-confirm="(data) => onRejectConfirm(String(data ?? ''))"
      param="驳回采购单的原因"
    />
  </div>
</template>

<style lang="scss" scoped>
.k-page {
  padding: 16px;
}
</style>
