<script lang="ts" setup>
import type { OrderQuery } from '@/api/mes/types'
import type { OrderDetailEntity } from '@/model/mes/order/OrderDetailEntity'
import type { OrderEntity } from '@/model/mes/order/OrderEntity'
import { AButton, APanel, ATable, DialogUtil, FeedbackUtil } from '@airpower/web'
import { computed, watch } from 'vue'

import { BillAuditOrReject, BillRejectDialog } from '@/component'
import { useBillTable } from '@/hook/billTable/useBillTable'
import { useOrderAudit, useOrderFinish, useOrderList, useOrderPause, useOrderReject, useOrderReport, useOrderStart } from '@/hook/mes/useOrder'
import { OrderService } from '@/model/mes/order/OrderService'
import { OrderStatusEnum } from '@/model/mes/order/OrderStatusEnum'
import { CustomerDetail } from '../../channel/customer/component'
import { OrderDetail, OrderEditor, OrderFinishEditor } from './component'

const hook = useBillTable(OrderService, {
  editView: OrderEditor,
  detailView: OrderDetail,
})

// 使用新 API Hook 替代 airpower service 的 getPage
// 透传 filter（billCode/type/status），保证搜索筛选条件到达新 API
const query = computed<OrderQuery>(() => {
  const filter = hook.request.value.filter
  return {
    page: hook.request.value.page.pageNum || 1,
    pageSize: hook.request.value.page.pageSize || 20,
    filter: {
      ...(filter?.billCode ? { billCode: filter.billCode } : {}),
      ...(filter?.type !== undefined && filter?.type !== null ? { type: filter.type } : {}),
      ...(filter?.status !== undefined && filter?.status !== null ? { status: filter.status } : {}),
    },
  }
})

const { data, isLoading } = useOrderList(query)

// 将新 API 响应同步到 airpower QueryResponsePage 格式，保持 ATable 兼容
watch(data, (value) => {
  if (value) {
    hook.response.value.list = value.list
    hook.response.value.total = value.total
  }
})

// 同步加载状态到 hook，保持 ATable v-loading 正常工作
watch(isLoading, (v) => {
  hook.isLoading.value = v
})

// ── Mutation: 审核 ──
const { mutateAsync: auditMutate } = useOrderAudit()
async function onAudit(bill: { id: number }) {
  await FeedbackUtil.confirmWarning('是否确认审核选择的生产订单？')
  await auditMutate({ id: bill.id })
}

// ── Mutation: 驳回 ──
const { mutateAsync: rejectMutate } = useOrderReject()
async function onReject(bill: { id: number, rejectReason?: string }) {
  const rejectReason: string = await DialogUtil.show(BillRejectDialog, '驳回生产订单的原因')
  await FeedbackUtil.confirmWarning('是否确认驳回选择的生产订单？')
  await rejectMutate({ id: bill.id, rejectReason })
}

// ── Mutation: 完成 ──
const { mutateAsync: finishMutate } = useOrderFinish()
async function onFinish(bill: { id: number }) {
  await FeedbackUtil.confirmWarning('是否确认设置生产订单已完成？')
  await finishMutate({ id: bill.id })
}

// ── Mutation: 报工 ──
const { mutateAsync: reportMutate } = useOrderReport()
async function onAddDetail(order: OrderEntity) {
  const quantity = order.quantity - order.finishQuantity
  const detail = await DialogUtil.show<OrderDetailEntity>(OrderFinishEditor, quantity)
  detail.billId = order.id
  await reportMutate(detail)
}

// ── Mutation: 开始/暂停 ──
const { mutateAsync: startMutate } = useOrderStart()
const { mutateAsync: pauseMutate } = useOrderPause()
function startOrPauseLabel(order: OrderEntity) {
  switch (order.status) {
    case OrderStatusEnum.PRODUCING.key:
      return '暂停'
    default:
      return '开始'
  }
}

async function onStartOrPause(order: OrderEntity) {
  await FeedbackUtil.confirmWarning(`是否确认${startOrPauseLabel(order)}订单？`, '操作提醒')
  switch (order.status) {
    case OrderStatusEnum.PRODUCING.key:
      await pauseMutate({ id: order.id })
      break
    default:
      await startMutate({ id: order.id })
  }
}
</script>

<template>
  <APanel>
    <ATable
      :disable-edit="(row) => !OrderStatusEnum.REJECTED.equalsKey(row.status)"
      :use-hook="hook"
      ctrl-width="260"
      hide-delete
      show-detail
    >
      <template #customer="{ data }">
        <el-link
          v-if="data.customer"
          @click="DialogUtil.show(CustomerDetail, data.customer)"
        >
          {{ data.customer.name }}
        </el-link>
        <template v-else>
          -
        </template>
      </template>
      <template #customRow="{ data }">
        <template v-if="OrderStatusEnum.AUDITING.equalsKey(data.status)">
          <BillAuditOrReject
            :bill="data"
            audit-permission="Order_audit"
            reject-permission="Order_reject"
            @on-audit="onAudit"
            @on-reject="onReject"
          />
        </template>
        <template
          v-if="
            [OrderStatusEnum.PREPARE.key, OrderStatusEnum.PRODUCING.key, OrderStatusEnum.PAUSED.key].includes(
              data.status,
            )
          "
        >
          <AButton
            :disabled="!OrderStatusEnum.PRODUCING.equalsKey(data.status)"
            link
            @click="onAddDetail(data)"
          >
            报工
          </AButton>
          <AButton
            link
            @click="onStartOrPause(data)"
          >
            {{ startOrPauseLabel(data) }}
          </AButton>
          <AButton
            :disabled="!OrderStatusEnum.PRODUCING.equalsKey(data.status)"
            link
            @click="onFinish(data)"
          >
            完成
          </AButton>
        </template>
      </template>
    </ATable>
  </APanel>
</template>

<style lang="scss" scoped></style>
