<script lang="ts" setup>
import type { IDialogActionProps } from '@/framework'
import type { MaterialEntity } from '@/model/asset/material/MaterialEntity'
import type { PurchaseDetailEntity } from '@/model/channel/purchase/PurchaseDetailEntity'
import type { SupplierEntity } from '@/model/channel/supplier/SupplierEntity'
import { ElForm, ElFormItem, ElInput, ElInputNumber } from 'element-plus'
import { computed, ref, watch } from 'vue'
import KDialog from '@/component/framework/KDialog.vue'
import KSelector from '@/component/framework/KSelector.vue'
import { FeedbackUtil, getFieldLabel, WebValidator } from '@/framework'
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

watch(
  () => props.param,
  (detail) => {
    // 复制一份再编辑：取消时不会把半截修改留在上一层的数组里
    form.value = detail ? (detail.copy() as PurchaseDetailEntity) : null
  },
  { immediate: true },
)

const rules = computed(() => WebValidator.createRules(PurchaseDetail as never) as unknown as Record<string, unknown[]>)

const unitName = computed(() => (form.value?.material as MaterialEntity | undefined)?.unit?.name ?? '')

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
    return
  }
  FeedbackUtil.toastWarning('该供应商未提供该物料的采购报价，将自动填写该物料的参考报价')
  detail.price = (detail.material as MaterialEntity).purchasePrice
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

function onConfirm() {
  if (form.value) {
    props.onConfirm?.(form.value)
  }
}
</script>

<template>
  <KDialog
    title="采购明细"
    width="620px"
    @cancel="() => props.onCancel?.()"
    @confirm="onConfirm"
  >
    <ElForm
      v-if="form"
      :model="form"
      :rules="rules"
      label-width="110px"
      @submit.prevent
    >
      <ElFormItem
        label="采购物料"
        prop="material"
      >
        <ElInput
          :model-value="form.material?.name ?? ''"
          placeholder="请选择采购物料..."
          readonly
          @click="materialPicking = true"
        />
      </ElFormItem>
      <ElFormItem
        label="供应商"
        prop="supplier"
      >
        <ElInput
          :model-value="(form.supplier as SupplierEntity | undefined)?.name ?? ''"
          placeholder="请选择供应商..."
          readonly
          @click="supplierPicking = true"
        />
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
.purchase-detail-editor__number {
  width: 180px;
}

.purchase-detail-editor__unit {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
}
</style>
