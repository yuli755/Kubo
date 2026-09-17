<script lang="ts" setup>
import type { IDialogActionProps } from '@/framework'
import type { SaleDetailEntity } from '@/model/channel/sale/SaleDetailEntity'
import type { SaleEntity } from '@/model/channel/sale/SaleEntity'
import { ElDescriptions, ElDescriptionsItem } from 'element-plus'
import { computed } from 'vue'
import KDetailTable from '@/component/framework/KDetailTable.vue'
import KDialog from '@/component/framework/KDialog.vue'
import { getCellText, getEnumLabel, getTableConfig } from '@/framework'
import { KSaleService } from '@/hook/channel/useSale'
import { useDetail } from '@/hook/kubo/useDetail'
import { SaleDetailEntity as SaleDetail } from '@/model/channel/sale/SaleDetailEntity'
import { SaleEntity as Sale } from '@/model/channel/sale/SaleEntity'
import { SaleStatusEnum } from '@/model/channel/sale/SaleStatusEnum'

/**
 * # 销售单详情（F4 批 5 · 新底座）
 *
 * **命令式弹窗**（`IDialogActionProps`）：列表「查看」与 wms 出库详情经
 * `openSaleDetail()` 接缝打开的是同一个组件。
 *
 * 与采购单详情的重要差别：**销售明细没有「作业」按钮**。
 * 旧页面就是这样 —— 销售的"完成"不是在销售单上登记，而是由 wms 出库单的
 * 「作业」（确认出库）驱动；这里保持只读，不擅自给销售单加动作。
 * （`Sale_addFinish` 权限码与后端端点仍在，界面入口维持旧口径。）
 */
const props = defineProps<IDialogActionProps<SaleEntity>>()

const detailId = computed(() => props.param?.id ?? null)

const detail = useDetail(KSaleService, {
  queryKey: ['channel:sale'],
  id: detailId,
})

const bill = computed<SaleEntity | undefined>(() => detail.entity.value)

const headerFields = ['billCode', 'status', 'customerName', 'reason', 'totalPrice', 'createTime', 'rejectReason'] as const

function fieldLabel(key: string): string {
  return getTableConfig(Sale, key)?.label ?? key
}

function fieldText(key: string): string {
  const entity = bill.value
  if (!entity) {
    return '-'
  }
  if (key === 'status') {
    return entity.status === undefined ? '-' : (getEnumLabel(SaleStatusEnum, entity.status) ?? '-')
  }
  // `customerName` 是后端冗余返回的扁平字段，取不到时回退到嵌套对象
  if (key === 'customerName') {
    return entity.customerName || (entity.customer as { name?: string } | undefined)?.name || '-'
  }
  return getCellText(entity, { ...(getTableConfig(Sale, key) ?? {}), key } as never)
}

function close() {
  props.onConfirm?.()
}
</script>

<template>
  <KDialog
    confirm-text="关闭"
    hide-cancel
    min-height="70%"
    title="销售详情"
    width="80%"
    @cancel="close"
    @confirm="close"
  >
    <ElDescriptions
      v-loading="detail.isLoading.value"
      :column="3"
      border
      size="small"
    >
      <ElDescriptionsItem
        v-for="field in headerFields"
        :key="field"
        :label="fieldLabel(field)"
      >
        {{ fieldText(field) }}
      </ElDescriptionsItem>
    </ElDescriptions>

    <div class="sale-detail__table">
      <KDetailTable
        :entity="SaleDetail"
        :exclude="['createTime', 'updateTime']"
        :rows="(bill?.details ?? []) as SaleDetailEntity[]"
        empty-text="这张销售单还没有明细"
      />
    </div>
  </KDialog>
</template>

<style lang="scss" scoped>
.sale-detail__table {
  margin-top: 12px;
}
</style>
