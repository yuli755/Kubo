<script lang="ts" setup>
import type { PurchaseDetailEntity } from '@/model/channel/purchase/PurchaseDetailEntity'
import { Plus } from '@element-plus/icons-vue'
import { ElButton, ElTag } from 'element-plus'
import { computed, ref } from 'vue'
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
 * 主表单（单号 + 采购事由）由 `KFormDialog` 按 `@KForm` 元数据渲染，
 * 明细数组放在 `#extra` 里用 `KDetailTable` 渲染（尚未提交给后端，没有服务/分页/搜索）。
 *
 * 三条业务判据逐条保真：
 * 1. **明细为空不发请求**（旧版 `FeedbackUtil.toastWarning('请添加明细后再提交')`，
 *    本批统一到与 wms 相同的 `ElMessage` 口径 —— 文案不变）；
 * 2. **明细增删要权限**：旧版 `v-permission="[add, update]"`，这里挂在 `KDetailTable` 的
 *    `row-actions.permission` 与「添加明细」按钮上（数组语义 = 满足其一即可，与指令同口径）；
 * 3. **删明细二次确认**（旧文案「是否删除选中行的采购明细？」）。
 *
 * 明细本身由 `PurchaseDetailEditor` 编辑：选物料 + 选供应商时会去查「该物料 × 该供应商」
 * 的专属采购价（`purchasePrice/getByMaterialAndSupplier`），查不到回退物料的参考报价。
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
  return details.value.reduce((sum, item) => {
    const qty = Number(item.quantity) || 0
    const prc = Number(item.price) || 0
    return sum + (qty * prc)
  }, 0)
})

function onLoaded(next: Record<string, unknown>) {
  next.details ??= []
  form.value = next
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
