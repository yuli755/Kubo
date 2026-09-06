<script lang="ts" setup>
import type { OrderDetailEntity } from '@/model/mes/order/OrderDetailEntity'
import type { OrderEntity } from '@/model/mes/order/OrderEntity'
import { AButton, APanel, ATable, DialogUtil, FeedbackUtil } from '@airpower/web'

import { BillAuditOrReject, BillRejectDialog } from '@/component'
import { Permissions } from '@/config/permissions'
import { vPermission } from '@/directives'
import { useOrderAudit, useOrderFinish, useOrderPause, useOrderReject, useOrderReport, useOrderStart } from '@/hook/mes/useOrder'
import { useTanStackTable } from '@/hook/useTanStackTable'
import { OrderService } from '@/model/mes/order/OrderService'
import { OrderStatusEnum } from '@/model/mes/order/OrderStatusEnum'
import { CustomerDetail } from '../../channel/customer/component'
import { OrderDetail, OrderEditor, OrderFinishEditor } from './component'

const hook = useTanStackTable(OrderService, {
  queryKey: ['mes:order'],
  editView: OrderEditor,
  detailView: OrderDetail,
})

async function refreshList() {
  hook.onReloadData()
}

const { mutateAsync: auditMutate } = useOrderAudit()
async function onAudit(bill: { id: number }) {
  await FeedbackUtil.confirmWarning('是否确认审核选择的生产订单？')
  await auditMutate({ id: bill.id })
  await refreshList()
}

const { mutateAsync: rejectMutate } = useOrderReject()
async function onReject(bill: { id: number, rejectReason?: string }) {
  const rejectReason: string = await DialogUtil.show(BillRejectDialog, '驳回生产订单的原因')
  await FeedbackUtil.confirmWarning('是否确认驳回选择的生产订单？')
  await rejectMutate({ id: bill.id, rejectReason })
  await refreshList()
}

const { mutateAsync: finishMutate } = useOrderFinish()
async function onFinish(bill: { id: number }) {
  await FeedbackUtil.confirmWarning('是否确认设置生产订单已完成？')
  await finishMutate({ id: bill.id })
  await refreshList()
}

const { mutateAsync: reportMutate } = useOrderReport()
async function onAddDetail(order: OrderEntity) {
  const quantity = order.quantity - order.finishQuantity
  const detail = await DialogUtil.show<OrderDetailEntity>(OrderFinishEditor, quantity)
  detail.billId = order.id
  await reportMutate(detail as unknown as import('@/api/mes/types').OrderReportRequest)
  await refreshList()
}

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
  await refreshList()
}
</script>

<template>
  <APanel>
    <ATable
      :disable-edit="(row) => !OrderStatusEnum.REJECTED.equalsKey(row.status)"
      :use-hook="hook"
      :add-permission="Permissions.Mes.Order.add"
      :detail-permission="Permissions.Mes.Order.getDetail"
      :edit-permission="Permissions.Mes.Order.update"
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
            :audit-permission="Permissions.Mes.Order.audit"
            :reject-permission="Permissions.Mes.Order.reject"
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
            v-permission="Permissions.Mes.Order.addOrderDetail"
            :disabled="!OrderStatusEnum.PRODUCING.equalsKey(data.status)"
            link
            @click="onAddDetail(data)"
          >
            报工
          </AButton>
          <AButton
            v-permission="[Permissions.Mes.Order.start, Permissions.Mes.Order.pause]"
            link
            @click="onStartOrPause(data)"
          >
            {{ startOrPauseLabel(data) }}
          </AButton>
          <AButton
            v-if="!OrderStatusEnum.PRODUCING.equalsKey(data.status)"
            v-permission="Permissions.Mes.Order.finish"
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
