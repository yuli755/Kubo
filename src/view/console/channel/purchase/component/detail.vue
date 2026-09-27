<script lang="ts" setup>
import type { IDialogActionProps } from '@/framework'
import type { PurchaseDetailEntity } from '@/model/channel/purchase/PurchaseDetailEntity'
import type { PurchaseEntity } from '@/model/channel/purchase/PurchaseEntity'
import { ElAlert, ElButton, ElDescriptions, ElDescriptionsItem, ElTag } from 'element-plus'
import { computed, ref } from 'vue'
import KDetailTable from '@/component/framework/KDetailTable.vue'
import KDialog from '@/component/framework/KDialog.vue'
import { Permissions } from '@/config/permissions'
import { FeedbackUtil, getCellText, getEnumLabel, getTableConfig } from '@/framework'
import { KPurchaseService } from '@/hook/channel/usePurchase'
import { useDetail } from '@/hook/kubo/useDetail'
import { PurchaseDetailEntity as PurchaseDetail } from '@/model/channel/purchase/PurchaseDetailEntity'
import { PurchaseEntity as Purchase } from '@/model/channel/purchase/PurchaseEntity'
import { PurchaseStatusEnum } from '@/model/channel/purchase/PurchaseStatusEnum'
import PurchaseAddFinishEditor from './addFinishEditor.vue'

/**
 * # 采购单详情（F4 批 5 · 新底座）
 *
 * **命令式弹窗**（`IDialogActionProps`）：列表「查看」与跨域的
 * `openPurchaseDetail()` 接缝都调 `DialogUtil.show(PurchaseDetail, bill)` —— 一个组件两个入口，
 * 不再有旧版 `DialogProps.withParam` 那套静态构造。
 *
 * 只读主表信息 + 明细表 + 行内「作业」：
 * - 主表字段按 `@KTable` 元数据渲染（`ElDescriptions`），字典/金额/时间的显示口径与列表一致；
 * - 「作业」按钮**只在「采购中」可点**（`PurchaseStatusEnum.PURCHASING`）：状态机后端也会再校验，
 *   迁移不许把它简化成"永远可点"；
 * - 明细的 `sale` 来源列沿用旧版口径：采购明细没有来源单据列，明细表只读。
 *
 * 旧版的 `BillFormCode` / `BillFormMoreDetail` 两个组件没有跟进 —— 它们只服务 airpower 页面，
 * 新底座用 `ElDescriptions` + `@KTable` 元数据就能表达同样的信息，少一个只在旧栈里存在的组件。
 */
const props = defineProps<IDialogActionProps<PurchaseEntity>>()

const detailId = computed(() => props.param?.id ?? null)

const detail = useDetail(KPurchaseService, {
  queryKey: ['channel:purchase'],
  id: detailId,
})

const bill = computed<PurchaseEntity | undefined>(() => detail.entity.value)

const headerFields = ['billCode', 'status', 'reason', 'totalPrice', 'totalRealPrice', 'createTime', 'rejectReason'] as const

function fieldLabel(key: string): string {
  return getTableConfig(Purchase, key)?.label ?? key
}

function fieldText(key: string): string {
  const entity = bill.value
  if (!entity) {
    return '-'
  }
  if (key === 'status') {
    return entity.status === undefined ? '-' : (getEnumLabel(PurchaseStatusEnum, entity.status) ?? '-')
  }
  return getCellText(entity, { ...(getTableConfig(Purchase, key) ?? {}), key } as never)
}

const statusTagType = computed<'danger' | 'warning' | 'primary' | 'success' | 'info'>(() => {
  const status = bill.value?.status
  if (status === undefined) {
    return 'info'
  }
  if (PurchaseStatusEnum.REJECTED.equalsKey(status)) {
    return 'danger'
  }
  if (PurchaseStatusEnum.AUDITING.equalsKey(status)) {
    return 'warning'
  }
  if (PurchaseStatusEnum.PURCHASING.equalsKey(status)) {
    return 'primary'
  }
  if (PurchaseStatusEnum.DONE.equalsKey(status)) {
    return 'success'
  }
  return 'info'
})

const isRejected = computed(() => {
  const status = bill.value?.status
  return status !== undefined && PurchaseStatusEnum.REJECTED.equalsKey(status)
})

/** 「作业」是否可点：只有「采购中」的单据能登记到货数量 */
function canFinish(): boolean {
  const status = bill.value?.status
  return status !== undefined && PurchaseStatusEnum.PURCHASING.equalsKey(status)
}

const finishing = ref<PurchaseDetailEntity | null>(null)

const hasPendingDetails = computed(() => {
  return (bill.value?.details ?? []).some((d) => {
    const waiting = Math.max(0, Number(d.quantity ?? 0) - Number(d.finishQuantity ?? 0))
    return waiting > 0
  })
})

async function finishAllDetails() {
  const pending = (bill.value?.details ?? []).filter((d) => {
    const waiting = Math.max(0, Number(d.quantity ?? 0) - Number(d.finishQuantity ?? 0))
    return waiting > 0 && d.id
  })
  if (!pending.length) {
    FeedbackUtil.toastWarning('没有待到货的采购明细')
    return
  }
  await FeedbackUtil.confirmWarning(
    `是否确认将剩余 ${pending.length} 项明细全部登记到货？系统将按待采购数量全额结清。`,
    '一键全部到货',
  )
  for (const d of pending) {
    const waiting = Math.max(0, Number(d.quantity ?? 0) - Number(d.finishQuantity ?? 0))
    await new KPurchaseService().addDetailFinishQuantity({
      id: d.id,
      quantity: waiting,
      billId: d.billId,
    } as never)
  }
  FeedbackUtil.toastSuccess('整单采购物资已全部登记到货')
  await detail.reload()
}

function openFinish(row: PurchaseDetailEntity) {
  finishing.value = row
}

async function onFinishConfirmed() {
  finishing.value = null
  await detail.reload()
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
    title="采购详情"
    width="80%"
    @cancel="close"
    @confirm="close"
  >
    <ElAlert
      v-if="isRejected || bill?.rejectReason"
      :closable="false"
      class="purchase-detail__alert"
      show-icon
      :title="`单据已被驳回${bill?.rejectReason ? `：${bill.rejectReason}` : ''}`"
      type="error"
    />

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
        <ElTag
          v-if="field === 'status'"
          :type="statusTagType"
          effect="light"
          size="small"
        >
          {{ fieldText('status') }}
        </ElTag>
        <span
          v-else-if="field === 'totalPrice' || field === 'totalRealPrice'"
          class="purchase-detail__amount"
        >
          {{ fieldText(field) }}
        </span>
        <span v-else>{{ fieldText(field) }}</span>
      </ElDescriptionsItem>
    </ElDescriptions>

    <div class="purchase-detail__table">
      <div class="purchase-detail__table-header">
        <div class="purchase-detail__table-title">
          <span>采购物资明细清单</span>
          <ElTag
            effect="plain"
            round
            size="small"
            type="info"
          >
            共 {{ bill?.details?.length ?? 0 }} 项
          </ElTag>
        </div>
        <div
          v-if="canFinish()"
          class="purchase-detail__table-tip"
        >
          <ElButton
            v-if="hasPendingDetails"
            size="small"
            type="success"
            plain
            @click="finishAllDetails"
          >
            一键全部到货
          </ElButton>
          <ElTag
            effect="plain"
            size="small"
            type="primary"
          >
            采购进行中 · 可点击操作列「作业」登记实际到货量
          </ElTag>
        </div>
      </div>

      <KDetailTable
        :entity="PurchaseDetail"
        :exclude="['createTime', 'updateTime']"
        :row-actions="[{
          label: '作业',
          permission: Permissions.Channel.Purchase.addFinish,
          disabled: () => !canFinish(),
          handler: (row: PurchaseDetailEntity) => openFinish(row),
        }]"
        :rows="(bill?.details ?? []) as PurchaseDetailEntity[]"
        empty-text="这张采购单还没有明细"
      />
    </div>

    <PurchaseAddFinishEditor
      v-if="finishing"
      :detail="finishing"
      :model-value="!!finishing"
      @confirmed="onFinishConfirmed"
      @update:model-value="value => !value && (finishing = null)"
    />
  </KDialog>
</template>

<style lang="scss" scoped>
.purchase-detail__alert {
  margin-bottom: 12px;
}

.purchase-detail__amount {
  font-weight: 600;
  color: var(--el-color-primary);
}

.purchase-detail__table {
  margin-top: 16px;
}

.purchase-detail__table-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.purchase-detail__table-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
</style>
