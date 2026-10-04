<script lang="ts" setup>
import type { MoveEntity } from '@/model/wms/move/MoveEntity'
import { ref } from 'vue'
import BillRejectDialog from '@/component/bill/BillRejectDialog.vue'
import BillStatusFilter from '@/component/bill/BillStatusFilter.vue'
import KPanel from '@/component/framework/KPanel.vue'
import KTable from '@/component/framework/KTable.vue'
import { Permissions } from '@/config/permissions'
import { FeedbackUtil } from '@/framework'
import { useTable } from '@/hook/kubo/useTable'
import { KMoveService } from '@/hook/wms/useMove'
import { MoveStatusEnum } from '@/model/wms/move/MoveStatusEnum'
import MoveDetail from './component/detail.vue'
import MoveEditor from './component/editor.vue'

/**
 * # 移库单列表（F4 批 4 · 新底座）
 *
 * 单据列表 = 普通列表 + 两个状态机动作（审核 / 驳回）。旧版把这两个动作放在 airpower 的
 * `#customRow` 插槽里用 `BillAuditOrReject` 组件渲染；新底座走 `KTable` 的 `:actions` ——
 * **判据仍然来自实体的状态机**（`canAudit()` / `canReject()` 由 `AbstractBaseBillEntity` 提供），
 * 组件不认识业务状态，只负责把 `disabled` 画出来。
 *
 * 状态分段筛选（Status Segmented Filter）：
 * 列表顶部引入全生命周期状态分段控制（全部 / 待审核 / 已驳回 / 移动中 / 已完成），
 * 与表格检索双向联动，消除多级下拉与频繁表头过滤交互冗余。
 *
 * 审核 / 驳回走 `KMoveService`（`AbstractBillService` 的 `audit`/`reject`），不再经
 * `api/wms` 的 typed client：同一件事两条链是死代码与口径漂移的温床（批 4 已删掉这两条链的
 * mutation hook 与 typed client）。
 *
 * 驳回原因用 `BillRejectDialog`（批 0 已迁到 `KDialog`，props 契约没变）：
 * 这里**声明式**地挂在页面上（`:on-confirm` / `:on-cancel`），而不是 `DialogUtil.show` ——
 * 命令式弹窗依赖 main.ts 注入的全局应用上下文，页面里少一处全局依赖就少一处测试盲区。
 */
const statusFilter = ref<number | null>(null)

const table = useTable(KMoveService, {
  queryKey: ['wms:move'],
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
const rejectingBill = ref<MoveEntity | null>(null)

function openCreate() {
  editingId.value = null
  editorVisible.value = true
}

function openEdit(row: MoveEntity) {
  editingId.value = row.id
  editorVisible.value = true
}

function openDetail(row: MoveEntity) {
  detailId.value = row.id
  detailVisible.value = true
}

async function onAudit(row: MoveEntity) {
  await FeedbackUtil.confirmWarning('是否确认审核选择的移库单？', '审核移库单')
  await new KMoveService().audit(row)
  FeedbackUtil.toastSuccess('审核成功')
  table.reload()
}

function onRejectStart(row: MoveEntity) {
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
  // 与旧页一致：填完原因后再确认一次（驳回是不可逆动作，多一道确认不是啰嗦）
  await FeedbackUtil.confirmWarning('是否确认驳回选择的移库单？', '驳回移库单')
  await new KMoveService().reject(bill, reason)
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
      title="移库单"
      description="仓库之间的库存移动：审核后逐条「作业」登记实际移动数量"
    >
      <BillStatusFilter
        v-model="statusFilter"
        :enum-class="MoveStatusEnum"
        :table="table"
      />

      <KTable
        :actions="[
          { label: '查看', permission: Permissions.Wms.Move.getDetail, handler: openDetail },
          { label: '审核', permission: Permissions.Wms.Move.audit, disabled: (row: MoveEntity) => !row.canAudit(), handler: onAudit },
          { label: '驳回', permission: Permissions.Wms.Move.reject, disabled: (row: MoveEntity) => !row.canReject(), handler: onRejectStart },
        ]"
        :disable-edit="(row: MoveEntity) => !MoveStatusEnum.REJECTED.equalsKey(row.status)"
        :permissions="{
          add: Permissions.Wms.Move.add,
          edit: Permissions.Wms.Move.update,
          export: Permissions.Wms.Move.export,
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

    <MoveEditor
      :id="editingId"
      v-model="editorVisible"
      @saved="onSaved"
    />

    <!-- 同 InputDetail：详情里「作业」移库完成后 emit('saved')，接住才能刷新列表。 -->
    <MoveDetail
      :id="detailId"
      v-model="detailVisible"
      @saved="onSaved"
    />

    <BillRejectDialog
      v-if="rejecting"
      :on-cancel="() => { rejecting = false; rejectingBill = null }"
      :on-confirm="(data) => onRejectConfirm(String(data ?? ''))"
      param="驳回移库单的原因"
    />
  </div>
</template>
