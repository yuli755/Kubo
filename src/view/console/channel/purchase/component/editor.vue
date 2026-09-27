<script lang="ts" setup>
import type { PurchaseDetailEntity } from '@/model/channel/purchase/PurchaseDetailEntity'
import { Plus, Search } from '@element-plus/icons-vue'
import { ElButton, ElIcon, ElInput, ElInputNumber, ElTag } from 'element-plus'
import { computed, ref, watch } from 'vue'
import KDetailTable from '@/component/framework/KDetailTable.vue'
import KFormDialog from '@/component/framework/KFormDialog.vue'
import KSelector from '@/component/framework/KSelector.vue'
import { Permissions } from '@/config/permissions'
import { DialogUtil, FeedbackUtil } from '@/framework'
import { KMaterialService } from '@/hook/asset/useMaterial'
import { KPurchaseService } from '@/hook/channel/usePurchase'
import { MaterialEntity } from '@/model/asset/material/MaterialEntity'
import { PurchaseDetailEntity as PurchaseDetail } from '@/model/channel/purchase/PurchaseDetailEntity'
import { playScanSuccess, playScanWarning } from '@/utils/audioFeedback'
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
 * 2. **扫码极速录入与批量选料**：支持扫码枪按条码回车自动追加/累加数量；支持多选批量加物料；
 * 3. **明细一键复制**：提供行级「复制」功能，免除反复打开弹窗录入相同物料；
 * 4. **明细为空不发请求**（旧版 `FeedbackUtil.toastWarning('请添加明细后再提交')`）；
 * 5. **明细增删要权限**：挂在 `KDetailTable` 的 `row-actions.permission` 与「添加明细」按钮上；
 * 6. **删明细二次确认**（「是否删除选中行的采购明细？」）。
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
const scanBarcode = ref('')
const batchPicking = ref(false)

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

function onScanEnter() {
  const code = scanBarcode.value.trim()
  if (!code) {
    playScanWarning()
    return
  }
  const found = details.value.find(d => d.materialCode === code || d.material?.code === code)
  if (found) {
    found.quantity = (Number(found.quantity) || 0) + 1
    playScanSuccess()
    FeedbackUtil.toastSuccess(`物料 [${code}] 数量累加至 ${found.quantity}`)
    scanBarcode.value = ''
    return
  }

  const detail = new PurchaseDetail()
  detail.material = Object.assign(new MaterialEntity(), {
    code,
    name: `物料 ${code}`,
  })
  detail.materialCode = code
  detail.materialName = `物料 ${code}`
  detail.quantity = 1
  detail.price = 0
  details.value.push(detail)
  playScanSuccess()
  FeedbackUtil.toastSuccess(`已快速录入采购物料 [${code}]，数量: 1`)
  scanBarcode.value = ''
}

function onBatchPicked(rows: unknown[]) {
  batchPicking.value = false
  const materials = rows as MaterialEntity[]
  if (!materials?.length) {
    return
  }
  for (const m of materials) {
    const detail = new PurchaseDetail()
    detail.material = m
    detail.materialName = m.name
    detail.materialCode = m.code
    detail.quantity = 1
    detail.price = m.purchasePrice ?? 0
    details.value.push(detail)
  }
  playScanSuccess()
  FeedbackUtil.toastSuccess(`已批量添加 ${materials.length} 条采购物料明细`)
}

function duplicateDetail(row: PurchaseDetailEntity) {
  const clone = row.copy() as PurchaseDetailEntity
  delete (clone as { id?: number }).id
  details.value.push(clone)
  playScanSuccess()
  FeedbackUtil.toastSuccess(`已复制物料 [${row.materialName || row.material?.name || '明细'}]`)
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
          <div class="purchase-editor__head-actions">
            <ElInput
              v-model="scanBarcode"
              placeholder="扫码枪扫入物料条码 (回车直接录入/累加)"
              size="small"
              clearable
              class="scan-input"
              @keyup.enter="onScanEnter"
            >
              <template #prefix>
                <ElIcon><Search /></ElIcon>
              </template>
            </ElInput>
            <ElButton
              v-permission="[Permissions.Channel.Purchase.add, Permissions.Channel.Purchase.update]"
              size="small"
              plain
              @click="batchPicking = true"
            >
              批量添加
            </ElButton>
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
        </div>

        <KDetailTable
          :entity="PurchaseDetail"
          :exclude="['createTime', 'updateTime']"
          :row-actions="[
            {
              label: '复制',
              permission: [Permissions.Channel.Purchase.add, Permissions.Channel.Purchase.update],
              handler: duplicateDetail,
            },
            {
              label: '删除',
              danger: true,
              permission: [Permissions.Channel.Purchase.add, Permissions.Channel.Purchase.update],
              handler: removeDetail,
            },
          ]"
          :rows="details"
          empty-text="还没有明细，支持扫码录入、批量挑选或手动新增"
        >
          <template #cell-quantity="{ row }">
            <ElInputNumber
              v-model="(row as PurchaseDetailEntity).quantity"
              :min="1"
              size="small"
              controls-position="right"
              style="width: 100px"
            />
          </template>
          <template #cell-price="{ row }">
            <ElInputNumber
              v-model="(row as PurchaseDetailEntity).price"
              :min="0"
              :precision="2"
              size="small"
              controls-position="right"
              style="width: 110px"
            />
          </template>
        </KDetailTable>
      </div>
    </template>
  </KFormDialog>

  <KSelector
    v-model="batchPicking"
    :service="KMaterialService"
    multiple
    title="批量挑选采购物料"
    @confirm="onBatchPicked"
  />
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
  gap: 12px;
}

.purchase-editor__head-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.purchase-editor__head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.scan-input {
  width: 250px;
}

.purchase-editor__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
</style>
