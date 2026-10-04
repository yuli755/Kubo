<script lang="ts" setup>
import type { FormInstance } from 'element-plus'
import type { IDialogActionProps } from '@/framework'
import type { MaterialEntity } from '@/model/asset/material/MaterialEntity'
import type { PurchaseDetailEntity } from '@/model/channel/purchase/PurchaseDetailEntity'
import type { SupplierEntity } from '@/model/channel/supplier/SupplierEntity'
import { ElButton, ElForm, ElFormItem, ElInput, ElInputNumber, ElTag } from 'element-plus'
import { computed, ref, watch } from 'vue'
import KDialog from '@/component/framework/KDialog.vue'
import KSelector from '@/component/framework/KSelector.vue'
import { FeedbackUtil, getFieldLabel, validateFormWithFeedback, WebValidator } from '@/framework'
import { KMaterialService } from '@/hook/asset/useMaterial'
import { KPurchasePriceService } from '@/hook/channel/usePurchasePrice'
import { KSupplierService } from '@/hook/channel/useSupplier'
import { PurchaseDetailEntity as PurchaseDetail } from '@/model/channel/purchase/PurchaseDetailEntity'

/**
 * # 采购明细编辑（F4 批 5 · 新底座）
 *
 * 编辑一条采购明细：选物料 + 选供应商 + 单价 + 数量。
 *
 * ## 报价回退链（旧版语义，逐字保真）
 * 选完物料与供应商后去查「该物料 × 该供应商」的专属采购价：
 * - 查到 → 单价用它；
 * - 查不到 → 提示「该供应商未提供该物料的采购报价，将自动填写该物料的参考报价」，
 *   单价回退到物料的 `purchasePrice`。
 *
 * `KPurchasePriceService.getByMaterialAndSupplier` 吃的是两个 id（内部组装成后端要的
 * `{ material: { id }, supplier: { id } }` 载荷），查不到时返回 `null` 而不是抛错 ——
 * 「没有专属报价」是正常业务状态，不该在页面上炸出一个红框。
 *
 * ## 与旧版的差别
 * - 物料/供应商选择器从 airpower `ASelect` + `MaterialSelector`/`SupplierSelector`
 *   换成 `KSelector` + `KMaterialService`/`KSupplierService`（选择器不再需要页面级弹窗契约）；
 * - 校验规则由 `WebValidator.createRules(PurchaseDetailEntity)` 从 `@KForm` 元数据生成
 *   （旧版读的是 airpower 注册表的 `PurchaseDetailService.createValidator()`）；
 * - 选中物料时一并回填 `materialId`/`materialName`：后端按 id 关联，而列表列读的是嵌套对象
 *   —— 两处都要有值，否则刚加进来的明细行要么提交不上、要么显示为空。
 */
const props = defineProps<IDialogActionProps<PurchaseDetailEntity>>()

const form = ref<PurchaseDetailEntity | null>(null)
const materialPicking = ref(false)
const supplierPicking = ref(false)
const priceSource = ref<'none' | 'agreement' | 'reference'>('none')

watch(
  () => props.param,
  (detail) => {
    // 复制一份再编辑：取消时不会把半截修改留在上一层的数组里
    form.value = detail ? (detail.copy() as PurchaseDetailEntity) : null
    priceSource.value = 'none'
  },
  { immediate: true },
)

const rules = computed(() => WebValidator.createRules(PurchaseDetail as never) as unknown as Record<string, unknown[]>)

const unitName = computed(() => (form.value?.material as MaterialEntity | undefined)?.unit?.name ?? '')

const subtotal = computed(() => {
  const price = Number(form.value?.price) || 0
  const qty = Number(form.value?.quantity) || 0
  return price * qty
})

/** 专属报价查询：物料 + 供应商都齐了才有意义 */
async function lookupPrice(): Promise<void> {
  const detail = form.value
  const materialId = detail?.material?.id
  const supplierId = (detail?.supplier as SupplierEntity | undefined)?.id
  if (!detail || !materialId || !supplierId) {
    return
  }
  const price = await new KPurchasePriceService().getByMaterialAndSupplier(materialId, supplierId)
  if (price) {
    detail.price = price.price
    priceSource.value = 'agreement'
    return
  }
  FeedbackUtil.toastWarning('该供应商未提供该物料的采购报价，将自动填写该物料的参考报价')
  detail.price = (detail.material as MaterialEntity).purchasePrice
  priceSource.value = 'reference'
}

function onMaterialPicked(rows: unknown[]) {
  materialPicking.value = false
  const material = rows[0] as MaterialEntity | undefined
  if (!form.value || !material) {
    return
  }
  form.value.material = material
  form.value.materialName = material.name
  void lookupPrice()
}

function onSupplierPicked(rows: unknown[]) {
  supplierPicking.value = false
  const supplier = rows[0] as SupplierEntity | undefined
  if (!form.value || !supplier) {
    return
  }
  form.value.supplier = supplier
  void lookupPrice()
}

const formRef = ref<FormInstance>()

/**
 * R52 反馈修复：原来「确定」直接 `props.onConfirm(form)` 交数据并让宿主卸窗，
 * `rules` 生成了却没人调 validate() —— 物料/供应商/单价/数量没填也被静默收进明细，
 * 用户完全不知道哪里没填。现在先走框架层统一校验反馈（标红 + 滚动定位 + toast 点名
 * 第一个出错字段），不通过就保留弹窗，通过才回传。
 */
async function onConfirm() {
  if (!form.value) {
    return
  }
  if (!await validateFormWithFeedback(formRef.value, PurchaseDetail)) {
    return
  }
  props.onConfirm?.(form.value)
}
</script>

<template>
  <KDialog
    title="采购明细"
    width="620px"
    :close-on-confirm="false"
    @cancel="() => props.onCancel?.()"
    @confirm="onConfirm"
  >
    <ElForm
      v-if="form"
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="110px"
      @submit.prevent
    >
      <ElFormItem
        label="采购物料"
        prop="material"
      >
        <div class="field-select-row">
          <ElInput
            :model-value="form.material?.name ?? ''"
            placeholder="请选择采购物料..."
            readonly
            @click="materialPicking = true"
          />
          <ElButton @click="materialPicking = true">
            选择
          </ElButton>
        </div>
      </ElFormItem>
      <ElFormItem
        label="供应商"
        prop="supplier"
      >
        <div class="field-select-row">
          <ElInput
            :model-value="(form.supplier as SupplierEntity | undefined)?.name ?? ''"
            placeholder="请选择供应商..."
            readonly
            @click="supplierPicking = true"
          />
          <ElButton @click="supplierPicking = true">
            选择
          </ElButton>
        </div>
      </ElFormItem>
      <ElFormItem
        :label="getFieldLabel(PurchaseDetail, 'price')"
        prop="price"
      >
        <ElInputNumber
          v-model="form.price as number"
          :min="0"
          :precision="2"
          class="purchase-detail-editor__number"
        />
        <span class="purchase-detail-editor__unit">元/{{ unitName }}</span>
        <ElTag
          v-if="priceSource === 'agreement'"
          size="small"
          type="success"
          effect="light"
          class="price-tag"
        >
          协议专属价
        </ElTag>
        <ElTag
          v-else-if="priceSource === 'reference'"
          size="small"
          type="warning"
          effect="light"
          class="price-tag"
        >
          物料参考价
        </ElTag>
      </ElFormItem>
      <ElFormItem
        :label="getFieldLabel(PurchaseDetail, 'quantity')"
        prop="quantity"
      >
        <ElInputNumber
          v-model="form.quantity as number"
          :min="0"
          class="purchase-detail-editor__number"
        />
        <span class="purchase-detail-editor__unit">{{ unitName }}</span>
      </ElFormItem>
      <ElFormItem label="小计金额">
        <span class="subtotal-val font-mono">¥ {{ subtotal.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) }}</span>
      </ElFormItem>
    </ElForm>
  </KDialog>

  <KSelector
    v-model="materialPicking"
    :selected="form?.material ? [form.material] : []"
    :service="KMaterialService"
    title="选择采购物料"
    @confirm="onMaterialPicked"
  />

  <KSelector
    v-model="supplierPicking"
    :selected="form?.supplier ? [form.supplier as SupplierEntity] : []"
    :service="KSupplierService"
    title="选择供应商"
    @confirm="onSupplierPicked"
  />
</template>

<style lang="scss" scoped>
.field-select-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.purchase-detail-editor__number {
  width: 180px;
}

.purchase-detail-editor__unit {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
}

.price-tag {
  margin-left: 8px;
}

.subtotal-val {
  font-size: 14px;
  font-weight: 700;
  color: var(--el-color-success-dark-2);
}
</style>
