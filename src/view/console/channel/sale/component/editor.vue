<script lang="ts" setup>
import type { CustomerEntity } from '@/model/channel/customer/CustomerEntity'
import type { SaleDetailEntity } from '@/model/channel/sale/SaleDetailEntity'
import type { SaleEntity } from '@/model/channel/sale/SaleEntity'
import { Plus, Search } from '@element-plus/icons-vue'
import { ElButton, ElIcon, ElInput, ElInputNumber, ElTag } from 'element-plus'
import { computed, ref, watch } from 'vue'
import KDetailTable from '@/component/framework/KDetailTable.vue'
import KFormDialog from '@/component/framework/KFormDialog.vue'
import KSelector from '@/component/framework/KSelector.vue'
import { Permissions } from '@/config/permissions'
import { DialogUtil, FeedbackUtil } from '@/framework'
import { KMaterialService } from '@/hook/asset/useMaterial'
import { KCustomerService } from '@/hook/channel/useCustomer'
import { KSaleService } from '@/hook/channel/useSale'
import { KSalePriceService } from '@/hook/channel/useSalePrice'
import { MaterialEntity } from '@/model/asset/material/MaterialEntity'
import { SaleDetailEntity as SaleDetail } from '@/model/channel/sale/SaleDetailEntity'
import { playScanSuccess, playScanWarning } from '@/utils/audioFeedback'
import SaleDetailEditor from './detailEditor.vue'

/**
 * # 销售单编辑器（F4 批 5 · 新底座）
 *
 * 与采购单编辑器同构（主表单元数据渲染 + 明细 `KDetailTable`），核心体验优化点：
 *
 * 1. **客户智能选择器**：旧版用 `ASelect + CustomerSelector` 选客户，这里用
 *    `KSelector + KCustomerService` 写进 `#field-customerId` 插槽，选中时同时回填
 *    `customer`（嵌套对象）与 `customerId`（后端关联用）—— 提交前再兜一次同步；
 * 2. **总金额明细自动核算**：`totalPrice` 字段基于销售明细自动汇总（单价 × 数量），
 *    只读展示，彻底杜绝人工口算或漏算输入错误；
 * 3. **扫码极速录入与批量选料**：支持扫码枪按条码回车直接录入或累加；支持多选批量加料；
 * 4. **明细一键克隆**：明细表格增设「复制」动作，方便相同物料分拆或调配；
 * 5. **明细继承单据客户**：新建明细时把表单上的客户塞进明细，明细编辑器据此查
 *    「该物料 × 该客户」的专属销售价。
 */
const props = defineProps<{
  modelValue: boolean
  /** 编辑的记录 id（不传为新增） */
  id?: number | null
  /** 克隆再来一单时传入的源单据（新增模式生效） */
  initialBill?: SaleEntity | null
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'saved'): void
}>()

const form = ref<Record<string, unknown>>({})
const details = computed<SaleDetailEntity[]>(() => (form.value.details ?? []) as SaleDetailEntity[])
const customerPicking = ref(false)
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
    }
  },
  { immediate: true },
)

const customerName = computed(() => (form.value.customer as CustomerEntity | undefined)?.name ?? '')

function onLoaded(next: Record<string, unknown>) {
  if (!props.id && props.initialBill) {
    const copy = props.initialBill.copy() as SaleEntity
    next.customer = copy.customer
    next.customerId = copy.customerId
    next.reason = copy.reason ? `[再来一单] ${copy.reason}` : '快速再来一单'
    next.details = (copy.details ?? []).map((d: SaleDetailEntity) => {
      const cloned = d.copy() as SaleDetailEntity
      delete (cloned as { id?: number }).id
      return cloned
    })
  }
  else {
    next.details ??= []
  }
  form.value = next
  if (totalAmount.value > 0) {
    next.totalPrice = totalAmount.value
  }
}

function onCustomerPicked(rows: unknown[]) {
  customerPicking.value = false
  const customer = rows[0] as CustomerEntity | undefined
  if (!customer) {
    return
  }
  form.value.customer = customer
  form.value.customerId = customer.id
}

async function addDetail() {
  const detail = new SaleDetail()
  // 明细带上单据客户：销售价按「物料 + 客户」查，缺客户就只能回退参考价
  const customer = form.value.customer as CustomerEntity | undefined
  if (customer?.id) {
    detail.customer = customer
  }
  const confirmed = await DialogUtil.show<SaleDetailEntity>(SaleDetailEditor, detail)
  if (confirmed) {
    details.value.push(confirmed)
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
    FeedbackUtil.toastSuccess(`销售物料 [${code}] 数量累加至 ${found.quantity}`)
    scanBarcode.value = ''
    return
  }

  const detail = new SaleDetail()
  const customer = form.value.customer as CustomerEntity | undefined
  if (customer?.id) {
    detail.customer = customer
  }
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
  FeedbackUtil.toastSuccess(`已快速录入销售物料 [${code}]，数量: 1`)
  scanBarcode.value = ''
}

async function onBatchPicked(rows: unknown[]) {
  batchPicking.value = false
  const materials = rows as MaterialEntity[]
  if (!materials?.length) {
    return
  }
  const customer = form.value.customer as CustomerEntity | undefined
  const salePriceService = new KSalePriceService()
  for (const m of materials) {
    const detail = new SaleDetail()
    if (customer?.id) {
      detail.customer = customer
    }
    detail.material = m
    detail.materialId = m.id
    detail.materialName = m.name
    detail.materialCode = m.code
    detail.quantity = 1
    let price = m.salePrice ?? 0
    if (customer?.id && m.id) {
      const agreement = await salePriceService.getByMaterialAndCustomer(m.id, customer.id).catch(() => null)
      if (agreement?.price) {
        price = agreement.price
      }
    }
    detail.price = price
    details.value.push(detail)
  }
  playScanSuccess()
  FeedbackUtil.toastSuccess(`已批量添加 ${materials.length} 条销售物料明细`)
}

function duplicateDetail(row: SaleDetailEntity) {
  const clone = row.copy() as SaleDetailEntity
  delete (clone as { id?: number }).id
  details.value.push(clone)
  playScanSuccess()
  FeedbackUtil.toastSuccess(`已复制物料 [${row.materialName || row.material?.name || '明细'}]`)
}

async function removeDetail(row: SaleDetailEntity) {
  const index = details.value.indexOf(row)
  if (index < 0) {
    return
  }
  await FeedbackUtil.confirmWarning('是否删除选中行的销售明细？', '删除明细')
  details.value.splice(index, 1)
}

function beforeSubmit(payload: Record<string, unknown>) {
  if (!(payload.details as unknown[] | undefined)?.length) {
    FeedbackUtil.toastWarning('请添加明细后再提交')
    return null
  }
  const customer = payload.customer as CustomerEntity | undefined
  if (customer?.id) {
    // 两个字段一起提交：后端按 customerId 关联，列表/详情列读嵌套的 customer
    payload.customerId = customer.id
  }
  payload.totalPrice = totalAmount.value
  return payload
}
</script>

<template>
  <KFormDialog
    :id="props.id"
    :model-value="props.modelValue"
    :before-submit="beforeSubmit"
    :columns="2"
    :service="KSaleService"
    title="销售单"
    width="900px"
    @closed="emit('update:modelValue', false)"
    @loaded="onLoaded"
    @saved="emit('saved')"
    @update:model-value="value => emit('update:modelValue', value)"
  >
    <template #field-customerId="{ field }">
      <div class="sale-editor__customer">
        <ElInput
          :model-value="customerName"
          :placeholder="`请选择${field.label}`"
          readonly
        />
        <ElButton @click="customerPicking = true">
          选择
        </ElButton>
      </div>
    </template>

    <template #field-totalPrice="{ form: f }">
      <ElInputNumber
        v-model="f.totalPrice as number"
        :min="0"
        :precision="2"
        disabled
        placeholder="由销售明细自动核算"
        style="width: 100%"
      />
    </template>

    <template #extra>
      <div class="sale-editor__details">
        <div class="sale-editor__head">
          <div class="sale-editor__head-left">
            <span class="sale-editor__title">销售明细</span>
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
          <div class="sale-editor__head-actions">
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
              v-permission="[Permissions.Channel.Sale.add, Permissions.Channel.Sale.update]"
              size="small"
              plain
              @click="batchPicking = true"
            >
              批量添加
            </ElButton>
            <ElButton
              v-permission="[Permissions.Channel.Sale.add, Permissions.Channel.Sale.update]"
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
          :entity="SaleDetail"
          :exclude="['createTime', 'updateTime']"
          :row-actions="[
            {
              label: '复制',
              permission: [Permissions.Channel.Sale.add, Permissions.Channel.Sale.update],
              handler: duplicateDetail,
            },
            {
              label: '删除',
              danger: true,
              permission: [Permissions.Channel.Sale.add, Permissions.Channel.Sale.update],
              handler: removeDetail,
            },
          ]"
          :rows="details"
          empty-text="还没有明细，支持扫码录入、批量挑选或手动新增"
        >
          <template #cell-quantity="{ row }">
            <ElInputNumber
              v-model="(row as SaleDetailEntity).quantity"
              :min="1"
              size="small"
              controls-position="right"
              style="width: 100px"
            />
          </template>
          <template #cell-price="{ row }">
            <ElInputNumber
              v-model="(row as SaleDetailEntity).price"
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
    v-model="customerPicking"
    :selected="form.customer ? [form.customer as CustomerEntity] : []"
    :service="KCustomerService"
    title="选择销售客户"
    @confirm="onCustomerPicked"
  />

  <KSelector
    v-model="batchPicking"
    :service="KMaterialService"
    multiple
    title="批量挑选销售物料"
    @confirm="onBatchPicked"
  />
</template>

<style lang="scss" scoped>
.sale-editor__customer {
  display: flex;
  gap: 8px;
  width: 100%;
}

.sale-editor__details {
  margin-top: 4px;
}

.sale-editor__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap; /* F55-1：390 竖屏实测整簇工具条被顶出视口右缘，换行兜底 */
  margin-bottom: 10px;
  gap: 8px 12px;
}

.sale-editor__head-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sale-editor__head-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap; /* F55-1：窄弹窗里扫码框与按钮排不下一行时换行，不被裁掉 */
  gap: 8px;
}

.scan-input {
  width: 250px;
  max-width: 100%; /* F55-1：扫码框跟随容器收缩，不撑破窄弹窗 */
}

.sale-editor__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
</style>
