<script lang="ts" setup>
import type { PurchaseDetailEntity } from '@/model/channel/purchase/PurchaseDetailEntity'
import { ElButton, ElForm, ElFormItem, ElInput, ElInputNumber } from 'element-plus'
import { computed, ref, watch } from 'vue'
import KDialog from '@/component/framework/KDialog.vue'
import { FeedbackUtil, getFieldLabel } from '@/framework'
import { KPurchaseService } from '@/hook/channel/usePurchase'
import { PurchaseDetailEntity as PurchaseDetail } from '@/model/channel/purchase/PurchaseDetailEntity'

/**
 * # 采购「作业」（F4 批 5 · 新底座）
 *
 * 给一条采购明细登记**本次到货数量**。
 *
 * ## 后端契约
 * `POST /purchase/addFinish` 的载荷是**明细**（`BaseBillController` 的固定契约），
 * `quantity` 是**本次**数量 —— 后端 `afterDetailFinishAdded` 做累加，传累计值会翻倍。
 * 所以默认值取「待采购数量 = 采购数量 − 已采购数量」，且 `<= 0` 时不允许确认。
 *
 * ## 与旧版的差别
 * 旧版走 `useBillDetail().addDetailFinishQuantity(detail, billId)`：
 * 先弹 `BillAddFinishDialog`（一个裸数字输入框）拿数量，再 `expose('id','quantity','billId')`
 * 后提交 —— 用户看不到物料、也看不到还剩多少没完成。这里把物料信息与待采购数量
 * 一起显示，默认值同一口径（旧版是 `ceil((quantity - finishQuantity) * 1e5) / 1e5`，这里取整显示）。
 *
 * 旧版 `BillAddFinishDialog` 仍在（`@/component/bill/`），它服务的是还没迁的页面；
 * 采购单迁完后本域不再需要它。
 */
const props = defineProps<{
  modelValue: boolean
  /** 待作业的明细 */
  detail: PurchaseDetailEntity | null
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'confirmed'): void
}>()

const quantity = ref(0)
const submitting = ref(false)

watch(
  () => [props.modelValue, props.detail] as const,
  ([opened, detail]) => {
    if (opened && detail) {
      // 默认「待采购数量」：部分完成后再次登记时，默认值若还是总量会被后端拒绝
      quantity.value = Math.max(0, Number(detail.quantity ?? 0) - Number(detail.finishQuantity ?? 0))
    }
  },
  { immediate: true },
)

const materialName = computed(() => props.detail?.material?.name ?? '-')
const unitName = computed(() => props.detail?.material?.unit?.name ?? '')
const waitingQuantity = computed(() =>
  Math.max(0, Number(props.detail?.quantity ?? 0) - Number(props.detail?.finishQuantity ?? 0)),
)

async function onConfirm() {
  const detail = props.detail
  if (!detail?.id) {
    return
  }
  submitting.value = true
  try {
    await new KPurchaseService().addDetailFinishQuantity({
      id: detail.id,
      quantity: quantity.value,
      billId: detail.billId,
    } as never)
    FeedbackUtil.toastSuccess('明细采购数量登记成功')
    // 顺序不可换：先 confirmed 再关窗。KDialog 已配 :close-on-confirm="false"，
    // 此刻组件仍在树上，emit 才能送达详情层（否则 isUnmounted 守卫静默吞掉）
    emit('confirmed')
    emit('update:modelValue', false)
  }
  finally {
    submitting.value = false
  }
}
</script>

<template>
  <KDialog
    :close-on-confirm="false"
    :disable-confirm="quantity <= 0"
    :model-value="modelValue"
    confirm-text="添加完成"
    title="添加完成数量"
    width="560px"
    @cancel="emit('update:modelValue', false)"
    @confirm="onConfirm"
    @update:model-value="value => emit('update:modelValue', value)"
  >
    <!-- 提交中只在内容上盖 loading：`KDialog` 没有 loading 属性 -->
    <ElForm
      v-loading="submitting"
      label-width="110px"
      @submit.prevent
    >
      <ElFormItem label="物料信息">
        <ElInput
          :model-value="materialName"
          disabled
        />
      </ElFormItem>
      <ElFormItem label="待采购数量">
        <ElInput
          :model-value="String(waitingQuantity)"
          disabled
        >
          <template #append>
            {{ unitName }}
          </template>
        </ElInput>
      </ElFormItem>
      <ElFormItem :label="getFieldLabel(PurchaseDetail, 'quantity')">
        <div class="field-quick-row">
          <ElInputNumber
            v-model="quantity"
            :min="0"
            :max="waitingQuantity > 0 ? waitingQuantity : undefined"
            class="purchase-finish-editor__number"
          />
          <span class="purchase-finish-editor__unit">{{ unitName }}</span>
          <ElButton
            size="small"
            type="primary"
            plain
            :disabled="waitingQuantity <= 0"
            @click="quantity = waitingQuantity"
          >
            全部完成（{{ waitingQuantity }} {{ unitName }}）
          </ElButton>
        </div>
      </ElFormItem>
    </ElForm>
  </KDialog>
</template>

<style lang="scss" scoped>
.field-quick-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.purchase-finish-editor__number {
  width: 180px;
}

.purchase-finish-editor__unit {
  margin-left: 2px;
  color: var(--el-text-color-secondary);
}
</style>
