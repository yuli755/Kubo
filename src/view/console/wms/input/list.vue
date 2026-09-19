<script lang="ts" setup>
import type { InputEntity } from '@/model/wms/input/InputEntity'
import { ref } from 'vue'
import BillRejectDialog from '@/component/bill/BillRejectDialog.vue'
import KPanel from '@/component/framework/KPanel.vue'
import KStat from '@/component/framework/KStat.vue'
import KTable from '@/component/framework/KTable.vue'
import { Permissions } from '@/config/permissions'
import { FeedbackUtil, WebColor } from '@/framework'
import { useResourceCount } from '@/hook/kubo/useResourceCount'
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
 * 三个与旧页面对齐的细节：
 * - **驳回复核两道**：先填原因（`BillRejectDialog`），再确认一次（旧页面就是这两步）；
 * - **只有「已驳回」的单据可编辑**：被驳回的单据要能改完再提交，审核中/入库中的不许改；
 * - **没有删除与启停**：后端 `@Extends(exclude = Curd.Delete)` 没有删除端点，
 *   单据也没有启用/禁用语义 —— 用 `hide-delete` / `hide-enable` 显式关掉，
 *   否则「没传权限码 ≠ 不显示」（`v-permission` 未传值时是不拦截的），用户会看到点下去必然报错的按钮。
 */
const table = useTable(KInputService, {
  queryKey: ['wms:input'],
  export: true,
})

/**
 * 列表上方的状态统计条：走后端 `getCount`（按状态各查一次，只回行数）。
 *
 * 后端这四个读出口一直有这个端点，但 2026-09-19 之前**前端一个消费者都没有** ——
 * 端点活在契约里、页面上看不见。统计条与表格共享同一份 `filter`，改搜索条件时一起变；
 * 点某一项 = 把该状态筛进表格（再点一次取消），不需要用户去搜索栏里找状态字段。
 */
const counts = useResourceCount(new KInputService(), {
  queryKey: ['wms:input'],
  filter: table.filter,
  items: () => [
    { field: 'status', key: InputStatusEnum.AUDITING.key, label: '审核中', color: WebColor.WARNING },
    { field: 'status', key: InputStatusEnum.REJECTED.key, label: '已驳回', color: WebColor.DANGER },
    { field: 'status', key: InputStatusEnum.INPUTTING.key, label: '入库中', color: WebColor.NORMAL },
    { field: 'status', key: InputStatusEnum.DONE.key, label: '已完成', color: WebColor.SUCCESS },
  ],
})

/** 点统计条：当前已按该状态筛选则取消，否则替换为该项 */
function onStatSelect(value: number | string) {
  table.search(table.filter.value.status === value ? {} : { status: value })
}

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
      <KStat
        :items="counts.cells.value"
        :loading="counts.isLoading.value"
        @select="onStatSelect"
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
      @saved="onSaved"
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
  padding: 16px;
}
</style>
