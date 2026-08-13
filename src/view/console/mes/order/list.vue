<script lang="ts" setup>
import type { OrderQuery } from '@/api/mes/types'
import type { OrderDetailEntity } from '@/model/mes/order/OrderDetailEntity'
import type { OrderEntity } from '@/model/mes/order/OrderEntity'
import { AButton, APanel, ATable, DialogUtil, FeedbackUtil } from '@airpower/web'
import { computed, watch } from 'vue'

import { BillAuditOrReject } from '@/component'
import { useBillTable } from '@/hook/billTable/useBillTable'
import { useOrderList } from '@/hook/mes/useOrder'
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

const { data: _orderData, isFetching, refetch } = useOrderList(query)

// 覆写 service.getPage，使用 @tanstack/vue-query 替代 airpower 的 AbstractCurdService.getPage
hook.service.getPage = async () => {
  const result = await refetch()
  return { list: result.data?.list ?? [], total: result.data?.total ?? 0 }
}

// 同步加载状态到 hook，保持 ATable v-loading 正常工作
watch(isFetching, (v) => {
  hook.isLoading.value = v
})

async function onAddDetail(order: OrderEntity) {
  const quantity = order.quantity - order.finishQuantity
  const detail = await DialogUtil.show<OrderDetailEntity>(OrderFinishEditor, quantity)
  detail.billId = order.id
  await OrderService.create(hook.isLoading).addOrderDetail(detail)
  hook.onGetList()
}

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
  const http = OrderService.create(hook.isLoading)
  switch (order.status) {
    case OrderStatusEnum.PRODUCING.key:
      await http.pause(order)
      break
    default:
      await http.start(order)
  }
  hook.onGetList()
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
            @on-audit="hook.onAudit"
            @on-reject="hook.onReject"
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
            @click="hook.setBillDetailsAllFinished(data)"
          >
            完成
          </AButton>
        </template>
      </template>
    </ATable>
  </APanel>
</template>

<style lang="scss" scoped></style>
