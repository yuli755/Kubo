<script lang="ts" setup>
import type { OutputEntity } from '@/model/wms/output/OutputEntity'
import { ref } from 'vue'
import BillRejectDialog from '@/component/bill/BillRejectDialog.vue'
import BillStatusFilter from '@/component/bill/BillStatusFilter.vue'
import KPanel from '@/component/framework/KPanel.vue'
import KTable from '@/component/framework/KTable.vue'
import { Permissions } from '@/config/permissions'
import { FeedbackUtil } from '@/framework'
import { useTable } from '@/hook/kubo/useTable'
import { KOutputService } from '@/hook/wms/useOutput'
import { OutputStatusEnum } from '@/model/wms/output/OutputStatusEnum'
import OutputDetail from './component/detail.vue'
import OutputEditor from './component/editor.vue'

/**
 * # 出库单列表（F4 批 4 · 新底座）
 *
 * 与入库单列表同构：普通列表 + 审核/驳回两个状态机动作，判据来自实体的
 * `canAudit()` / `canReject()`。
 *
 * 状态分段筛选（Status Segmented Filter）：
 * 列表顶部引入全生命周期状态分段控制（全部 / 待审核 / 已驳回 / 出库中 / 已完成），
 * 与表格检索双向联动，消除多级下拉与频繁表头过滤交互冗余。
 *
 * 旧页面这里还有个**文案错误**：列表页 actions 用的权限码与文案是对得上的，
 * 但详情页把 `sale`（销售单）标成了「采购单」—— 已在 `OutputEntity` 的 K 侧元数据与
 * 详情页改正（见 `output/component/detail.vue` 的说明），列表侧不受影响。
 */
const statusFilter = ref<number | null>(null)

const table = useTable(KOutputService, {
  queryKey: ['wms:output'],
  export: true,
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

const detailVisible = ref(false)
const detailId = ref<number | null>(null)

const rejecting = ref(false)
const rejectingBill = ref<OutputEntity | null>(null)

function openCreate() {
  editingId.value = null
  editorVisible.value = true
}

function openEdit(row: OutputEntity) {
  editingId.value = row.id
  editorVisible.value = true
}

function openDetail(row: OutputEntity) {
  detailId.value = row.id
  detailVisible.value = true
}

async function onAudit(row: OutputEntity) {
  await FeedbackUtil.confirmWarning('是否确认审核选择的出库单？', '审核出库单')
  await new KOutputService().audit(row)
  FeedbackUtil.toastSuccess('审核成功')
  table.reload()
}

function onRejectStart(row: OutputEntity) {
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
  await FeedbackUtil.confirmWarning('是否确认驳回选择的出库单？', '驳回出库单')
  await new KOutputService().reject(bill, reason)
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
      title="出库单"
      description="销售出库 / 移动出库 / 普通出库：审核后逐条「作业」选择库存来源并登记出库数量"
    >
      <BillStatusFilter
        v-model="statusFilter"
        :enum-class="OutputStatusEnum"
        :table="table"
      />

      <KTable
        :actions="[
          { label: '查看', permission: Permissions.Wms.Output.getDetail, handler: openDetail },
          { label: '审核', permission: Permissions.Wms.Output.audit, disabled: (row: OutputEntity) => !row.canAudit(), handler: onAudit },
          { label: '驳回', permission: Permissions.Wms.Output.reject, disabled: (row: OutputEntity) => !row.canReject(), handler: onRejectStart },
        ]"
        :disable-edit="(row: OutputEntity) => !OutputStatusEnum.REJECTED.equalsKey(row.status)"
        :permissions="{
          add: Permissions.Wms.Output.add,
          edit: Permissions.Wms.Output.update,
          export: Permissions.Wms.Output.export,
        }"
        :table="table"
        hide-delete
        hide-enable
        show-export
        @add="openCreate"
        @edit="openEdit"
        @row-dblclick="openDetail"
      />
    </KPanel>

    <OutputEditor
      :id="editingId"
      v-model="editorVisible"
      @saved="onSaved"
    />

    <!-- 同 InputDetail：详情里「作业」出库后 emit('saved')，接住才能刷新列表状态。 -->
    <OutputDetail
      :id="detailId"
      v-model="detailVisible"
      @saved="onSaved"
    />

    <BillRejectDialog
      v-if="rejecting"
      :on-cancel="() => { rejecting = false; rejectingBill = null }"
      :on-confirm="(data) => onRejectConfirm(String(data ?? ''))"
      param="驳回出库单的原因"
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
