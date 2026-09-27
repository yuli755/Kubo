<script lang="ts" setup>
import type { InputEntity } from '@/model/wms/input/InputEntity'
import { ref } from 'vue'
import BillRejectDialog from '@/component/bill/BillRejectDialog.vue'
import BillStatusFilter from '@/component/bill/BillStatusFilter.vue'
import KPanel from '@/component/framework/KPanel.vue'
import KTable from '@/component/framework/KTable.vue'
import { Permissions } from '@/config/permissions'
import { FeedbackUtil } from '@/framework'
import { useTable } from '@/hook/kubo/useTable'
import { KInputService } from '@/hook/wms/useInput'
import { InputStatusEnum } from '@/model/wms/input/InputStatusEnum'
import InputDetail from './component/detail.vue'
import InputEditor from './component/editor.vue'

/**
 * # 入库单列表（F4 批 4 · 新底座）
 *
 * 与批 3 的普通列表相同，只多两个状态机动作（审核 / 驳回）：
 * 判据来自实体的 `canAudit()` / `canReject()`（`AbstractBaseBillEntity`），组件不认识业务状态。
 *
 * 状态分段筛选（Status Segmented Filter）：
 * 列表顶部引入全生命周期状态分段控制（全部 / 待审核 / 已驳回 / 入库中 / 已完成），
 * 与表格检索双向联动，消除多级下拉与频繁表头过滤交互冗余。
 *
 * 三个与旧页面对齐的细节：
 * - **驳回复核两道**：先填原因（`BillRejectDialog`），再确认一次（旧页面就是这两步）；
 * - **只有「已驳回」的单据可编辑**：被驳回的单据要能改完再提交，审核中/入库中的不许改；
 * - **没有删除与启停**：后端 `@Extends(exclude = Curd.Delete)` 没有删除端点，
 *   单据也没有启用/禁用语义 —— 用 `hide-delete` / `hide-enable` 显式关掉，
 *   否则「没传权限码 ≠ 不显示」（`v-permission` 未传值时是不拦截的），用户会看到点下去必然报错的按钮。
 */
const statusFilter = ref<number | null>(null)

const table = useTable(KInputService, {
  queryKey: ['wms:input'],
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
const rejectingBill = ref<InputEntity | null>(null)

function openCreate() {
  editingId.value = null
  editorVisible.value = true
}

function openEdit(row: InputEntity) {
  editingId.value = row.id
  editorVisible.value = true
}

function openDetail(row: InputEntity) {
  detailId.value = row.id
  detailVisible.value = true
}

async function onAudit(row: InputEntity) {
  await FeedbackUtil.confirmWarning('是否确认审核选择的入库单？', '审核入库单')
  await new KInputService().audit(row)
  FeedbackUtil.toastSuccess('审核成功')
  table.reload()
}

function onRejectStart(row: InputEntity) {
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
  await FeedbackUtil.confirmWarning('是否确认驳回选择的入库单？', '驳回入库单')
  await new KInputService().reject(bill, reason)
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
      title="入库单"
      description="采购入库 / 移动入库 / 普通入库：审核后逐条「作业」登记实际入库数量"
    >
      <BillStatusFilter
        v-model="statusFilter"
        :enum-class="InputStatusEnum"
        :table="table"
      />

      <KTable
        :actions="[
          { label: '查看', permission: Permissions.Wms.Input.getDetail, handler: openDetail },
          { label: '审核', permission: Permissions.Wms.Input.audit, disabled: (row: InputEntity) => !row.canAudit(), handler: onAudit },
          { label: '驳回', permission: Permissions.Wms.Input.reject, disabled: (row: InputEntity) => !row.canReject(), handler: onRejectStart },
        ]"
        :disable-edit="(row: InputEntity) => !InputStatusEnum.REJECTED.equalsKey(row.status)"
        :permissions="{
          add: Permissions.Wms.Input.add,
          edit: Permissions.Wms.Input.update,
          export: Permissions.Wms.Input.export,
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

    <InputEditor
      :id="editingId"
      v-model="editorVisible"
      @saved="onSaved"
    />

    <InputDetail
      :id="detailId"
      v-model="detailVisible"
    />

    <BillRejectDialog
      v-if="rejecting"
      :on-cancel="() => { rejecting = false; rejectingBill = null }"
      :on-confirm="(data) => onRejectConfirm(String(data ?? ''))"
      param="驳回入库单的原因"
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
