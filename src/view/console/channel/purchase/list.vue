<script lang="ts" setup>
import type { PurchaseEntity } from '@/model/channel/purchase/PurchaseEntity'
import { ref } from 'vue'
import BillRejectDialog from '@/component/bill/BillRejectDialog.vue'
import BillStatusFilter from '@/component/bill/BillStatusFilter.vue'
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
 * 采购单从创建到入库完整闭环：
 * 1. 制单：填供应商、采购事由与采购明细（含单价/数量），生成草稿态（审核中）；
 * 2. 审核/驳回：状态机动作（`AbstractBaseBillEntity.canAudit / canReject`）；
 * 3. 驳回可修改：驳回态允许重新编辑后再次提交；
 * 4. 作业：审核通过后（采购中）逐条「作业」登记实际到货（采购入库单）。
 *
 * 状态分段筛选（Status Segmented Filter）：
 * 列表顶部引入全生命周期状态分段控制（全部 / 待审核 / 已驳回 / 采购中 / 入库中 / 已完成），
 * 与表格检索双向联动，极大缩短高频运营动线。
 *
 * 「采购单没有删除端点也没有启停语义」：后端 `PurchaseController` 上写着
 * `exclude = Curd.Delete`，`@Extends` 也没有加回 Enable/Disable ——
 * 而 `v-permission` 遇到未传的权限码是不拦截的，所以 `hide-delete` / `hide-enable`
 * 必须显式给，否则用户会看到点下去必然报错的按钮。
 *
 * 「查看」保持命令式打开（`DialogUtil.show`）：`channel/purchase/component/openDetail.ts`
 * 这个跨域接缝也走同一个组件，wms 入库详情点「采购单号」时用的就是它。
 */
const statusFilter = ref<number | null>(null)

const table = useTable(KPurchaseService, {
  queryKey: ['channel:purchase'],
  beforeSearch: (f) => {
    const next = { ...f }
    if (next.status !== undefined && next.status !== '') {
      statusFilter.value = Number(next.status)
    }
    else if (statusFilter.value !== null && statusFilter.value !== undefined) {
      next.status = statusFilter.value
    }
    else {
      delete next.status
    }
    return next
  },
})

const editorVisible = ref(false)
const editingId = ref<number | null>(null)

const rejecting = ref(false)
const rejectingBill = ref<PurchaseEntity | null>(null)
const cloningBill = ref<PurchaseEntity | null>(null)

function openCreate() {
  cloningBill.value = null
  editingId.value = null
  editorVisible.value = true
}

function openEdit(row: PurchaseEntity) {
  cloningBill.value = null
  editingId.value = row.id
  editorVisible.value = true
}

async function onCloneBill(row: PurchaseEntity) {
  const detail = await new KPurchaseService().getDetail(row.id)
  cloningBill.value = detail
  editingId.value = null
  editorVisible.value = true
  FeedbackUtil.toastSuccess(`已载入采购单 [${row.billCode || row.id}] 的物料明细，可直接确认`)
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
      <BillStatusFilter
        v-model="statusFilter"
        :enum-class="PurchaseStatusEnum"
        :table="table"
      />

      <KTable
        :actions="[
          { label: '查看', permission: Permissions.Channel.Purchase.getDetail, handler: openDetail },
          { label: '审核', permission: Permissions.Channel.Purchase.audit, disabled: (row: PurchaseEntity) => !row.canAudit(), handler: onAudit },
          { label: '驳回', permission: Permissions.Channel.Purchase.reject, disabled: (row: PurchaseEntity) => !row.canReject(), handler: onRejectStart },
          { label: '再来一单', permission: Permissions.Channel.Purchase.add, handler: onCloneBill },
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
        @row-dblclick="openDetail"
      />
    </KPanel>

    <PurchaseEditor
      :id="editingId"
      v-model="editorVisible"
      :initial-bill="cloningBill"
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
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
}
</style>
