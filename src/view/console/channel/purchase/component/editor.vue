<script lang="ts" setup>
import type { PurchaseDetailEntity } from '@/model/channel/purchase/PurchaseDetailEntity'
import { Plus } from '@element-plus/icons-vue'
import { ElButton, ElInputNumber, ElTag } from 'element-plus'
import { computed, ref, watch } from 'vue'
import KDetailTable from '@/component/framework/KDetailTable.vue'
import KFormDialog from '@/component/framework/KFormDialog.vue'
import { Permissions } from '@/config/permissions'
import { DialogUtil, FeedbackUtil } from '@/framework'
import { KPurchaseService } from '@/hook/channel/usePurchase'
import { PurchaseDetailEntity as PurchaseDetail } from '@/model/channel/purchase/PurchaseDetailEntity'
import PurchaseDetailEditor from './detailEditor.vue'

/**
 * # 采购单编辑器（F4 批 5 · 新底座）
 *
 * 主表单（单号 + 采购事由 + 金额）由 `KFormDialog` 按 `@KForm` 元数据渲染，
 * 明细数组放在 `#extra` 里用 `KDetailTable` 渲染（尚未提交给后端，没有服务/分页/搜索）。
 *
 * 核心优化与业务规则：
 * 1. **字段能选择绝不让用户手工计算**：`totalPrice` 与 `totalRealPrice` 自动基于采购明细项
 *    实时核算（单价 × 数量），并在表单中联动同步，避免人工心算输入出错；
 * 2. **明细为空不发请求**（旧版 `FeedbackUtil.toastWarning('请添加明细后再提交')`）；
 * 3. **明细增删要权限**：挂在 `KDetailTable` 的 `row-actions.permission` 与「添加明细」按钮上；
 * 4. **删明细二次确认**（「是否删除选中行的采购明细？」）。
 */
const props = defineProps<{
  modelValue: boolean
  /** 编辑的记录 id（不传为新增） */
  id?: number | null
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'saved'): void
}>()

const form = ref<Record<string, unknown>>({})
const details = computed<PurchaseDetailEntity[]>(() => (form.value.details ?? []) as PurchaseDetailEntity[])

const totalAmount = computed(() => {
  const sum = details.value.reduce((acc, item) => {
    const qty = Number(item.quantity) || 0
    const prc = Number(item.price) || 0
    return acc + (qty * prc)
  }, 0)
  return Math.round(sum * 100) / 100
})

watch(
  totalAmount,
  (val) => {
    if (form.value) {
      form.value.totalPrice = val
      if (
        form.value.totalRealPrice === undefined
        || form.value.totalRealPrice === null
        || Number(form.value.totalRealPrice) === 0
      ) {
        form.value.totalRealPrice = val
      }
    }
  },
  { immediate: true },
)

function onLoaded(next: Record<string, unknown>) {
  next.details ??= []
  form.value = next
  if (totalAmount.value > 0) {
    next.totalPrice = totalAmount.value
    if (next.totalRealPrice === undefined || next.totalRealPrice === null) {
      next.totalRealPrice = totalAmount.value
    }
  }
}

async function addDetail() {
  const detail = await DialogUtil.show<PurchaseDetailEntity>(PurchaseDetailEditor, new PurchaseDetail())
  if (detail) {
    details.value.push(detail)
  }
}

async function removeDetail(row: PurchaseDetailEntity) {
  const index = details.value.indexOf(row)
  if (index < 0) {
    return
  }
  await FeedbackUtil.confirmWarning('是否删除选中行的采购明细？', '删除明细')
  details.value.splice(index, 1)
}

function beforeSubmit(payload: Record<string, unknown>) {
  if (!(payload.details as unknown[] | undefined)?.length) {
    FeedbackUtil.toastWarning('请添加明细后再提交')
    return null
  }
  payload.totalPrice = totalAmount.value
  if (payload.totalRealPrice === undefined || payload.totalRealPrice === null || payload.totalRealPrice === '') {
    payload.totalRealPrice = totalAmount.value
  }
  return payload
}
</script>

<template>
  <KFormDialog
    :id="props.id"
    :model-value="props.modelValue"
    :before-submit="beforeSubmit"
    :columns="2"
    :service="KPurchaseService"
    title="采购单"
    width="900px"
    @closed="emit('update:modelValue', false)"
    @loaded="onLoaded"
    @saved="emit('saved')"
    @update:model-value="value => emit('update:modelValue', value)"
  >
    <template #field-totalPrice="{ form: f }">
      <ElInputNumber
        v-model="f.totalPrice as number"
        :min="0"
        :precision="2"
        disabled
        placeholder="由采购明细自动核算"
        style="width: 100%"
      />
    </template>

    <template #field-totalRealPrice="{ form: f }">
      <ElInputNumber
        v-model="f.totalRealPrice as number"
        :min="0"
        :precision="2"
        placeholder="实际金额（默认与总额一致）"
        style="width: 100%"
      />
    </template>

    <template #extra>
      <div class="purchase-editor__details">
        <div class="purchase-editor__head">
          <div class="purchase-editor__head-left">
            <span class="purchase-editor__title">采购明细</span>
            <ElTag
              effect="plain"
              round
              size="small"
              type="info"
            >
              共 {{ details.length }} 项
            </ElTag>
            <ElTag
              v-if="details.length > 0"
              effect="light"
              round
              size="small"
              type="success"
            >
              总金额: ¥{{ totalAmount.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) }}
            </ElTag>
          </div>
          <ElButton
            v-permission="[Permissions.Channel.Purchase.add, Permissions.Channel.Purchase.update]"
            :icon="Plus"
            size="small"
            type="primary"
            @click="addDetail"
          >
            添加明细
          </ElButton>
        </div>

        <KDetailTable
          :entity="PurchaseDetail"
          :exclude="['createTime', 'updateTime']"
          :row-actions="[{
            label: '删除',
            danger: true,
            permission: [Permissions.Channel.Purchase.add, Permissions.Channel.Purchase.update],
            handler: removeDetail,
          }]"
          :rows="details"
          empty-text="还没有明细，点「添加明细」选一条物料"
        />
      </div>
    </template>
  </KFormDialog>
</template>

<style lang="scss" scoped>
.purchase-editor__details {
  margin-top: 4px;
}

.purchase-editor__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.purchase-editor__head-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.purchase-editor__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
</style>
