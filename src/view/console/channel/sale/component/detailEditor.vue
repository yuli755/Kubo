<script lang="ts" setup>
import type { FormInstance } from 'element-plus'
import type { IDialogActionProps } from '@/framework'
import type { MaterialEntity } from '@/model/asset/material/MaterialEntity'
import type { SaleDetailEntity } from '@/model/channel/sale/SaleDetailEntity'
import { ElButton, ElForm, ElFormItem, ElInput, ElInputNumber, ElTag } from 'element-plus'
import { computed, ref, watch } from 'vue'
import KDialog from '@/component/framework/KDialog.vue'
import KSelector from '@/component/framework/KSelector.vue'
import { FeedbackUtil, getFieldLabel, validateFormWithFeedback, WebValidator } from '@/framework'
import { KMaterialService } from '@/hook/asset/useMaterial'
import { KSalePriceService } from '@/hook/channel/useSalePrice'
import { SaleDetailEntity as SaleDetail } from '@/model/channel/sale/SaleDetailEntity'

/**
 * # 销售明细编辑（F4 批 5 · 新底座）
 *
 * 编辑一条销售明细：选物料 + 单价 + 数量。客户不在明细里选 —— 它是**单据主表**字段，
 * 由销售单编辑器在「添加明细」时塞进明细（`detail.customer`），这里只读展示。
 *
 * ## 报价回退链（旧版语义，逐字保真）
 * 选中物料后按「物料 × 该明细的客户」查专属销售价：
 * - 查到 → 单价用它；
 * - 查不到 → 提示「未配置该物料对该客户的特别销售价，将自动填写该物料的参考销售价」，
 *   单价回退到物料的 `salePrice`。
 *
 * 旧版 `afterGetDetail` 里还手工补过 `customerName` / `materialId` / `materialName`
 * 三个扁平字段 —— 新底座里列渲染读的是嵌套对象（`@KTable` 的 formatter），
 * 只需要保证 `materialId` 与 `material` 一起回填（后端按 id 关联）。
 */
const props = defineProps<IDialogActionProps<SaleDetailEntity>>()

const form = ref<SaleDetailEntity | null>(null)
const materialPicking = ref(false)
const priceSource = ref<'none' | 'agreement' | 'reference'>('none')

watch(
  () => props.param,
  (detail) => {
    // 复制一份再编辑：取消时不会把半截修改留在上一层的数组里
    form.value = detail ? (detail.copy() as SaleDetailEntity) : null
    priceSource.value = 'none'
  },
  { immediate: true },
)

const rules = computed(() => WebValidator.createRules(SaleDetail as never) as unknown as Record<string, unknown[]>)

const unitName = computed(() => (form.value?.material as MaterialEntity | undefined)?.unit?.name ?? '')
const customerName = computed(() => (form.value?.customer as { name?: string } | undefined)?.name ?? '-')

const subtotal = computed(() => {
  const price = Number(form.value?.price) || 0
  const qty = Number(form.value?.quantity) || 0
  return price * qty
})

/** 专属销售价查询：物料 + 客户都齐了才有意义 */
async function lookupPrice(): Promise<void> {
  const detail = form.value
  const materialId = detail?.material?.id ?? detail?.materialId
  const customerId = (detail?.customer as { id?: number } | undefined)?.id
  if (!detail || !materialId || !customerId) {
    return
  }
  const price = await new KSalePriceService().getByMaterialAndCustomer(materialId, customerId)
  if (price) {
    detail.price = price.price
    priceSource.value = 'agreement'
    return
  }
  FeedbackUtil.toastWarning('未配置该物料对该客户的特别销售价，将自动填写该物料的参考销售价')
  detail.price = (detail.material as MaterialEntity).salePrice
  priceSource.value = 'reference'
}

function onMaterialPicked(rows: unknown[]) {
  materialPicking.value = false
  const material = rows[0] as MaterialEntity | undefined
  if (!form.value || !material) {
    return
  }
  form.value.material = material
  form.value.materialId = material.id
  form.value.materialName = material.name
  void lookupPrice()
}

const formRef = ref<FormInstance>()

/**
 * R52 反馈修复：原来「确定」直接 `props.onConfirm(form)` 交数据并让宿主卸窗，
 * `rules` 生成了却没人调 validate() —— 物料/单价/数量没填也被静默收进明细，
 * 用户完全不知道哪里没填。现在先走框架层统一校验反馈（标红 + 滚动定位 + toast 点名
 * 第一个出错字段），不通过就保留弹窗，通过才回传。
 */
async function onConfirm() {
  if (!form.value) {
    return
  }
  if (!await validateFormWithFeedback(formRef.value, SaleDetail)) {
    return
  }
  props.onConfirm?.(form.value)
}
</script>

<template>
  <KDialog
    title="销售明细"
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
        label="销售物料"
        prop="material"
      >
        <div class="field-select-row">
          <ElInput
            :model-value="form.material?.name ?? ''"
            placeholder="请选择销售物料..."
            readonly
            @click="materialPicking = true"
          />
          <ElButton @click="materialPicking = true">
            选择
          </ElButton>
        </div>
      </ElFormItem>
      <ElFormItem label="销售客户">
        <ElInput
          :model-value="customerName"
          disabled
        />
      </ElFormItem>
      <ElFormItem
        :label="getFieldLabel(SaleDetail, 'price')"
        prop="price"
      >
        <ElInputNumber
          v-model="form.price as number"
          :min="0"
          :precision="2"
          class="sale-detail-editor__number"
        />
        <span class="sale-detail-editor__unit">元/{{ unitName }}</span>
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
        :label="getFieldLabel(SaleDetail, 'quantity')"
        prop="quantity"
      >
        <ElInputNumber
          v-model="form.quantity as number"
          :min="0"
          class="sale-detail-editor__number"
        />
        <span class="sale-detail-editor__unit">{{ unitName }}</span>
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
    title="选择销售物料"
    @confirm="onMaterialPicked"
  />
</template>

<style lang="scss" scoped>
.field-select-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.sale-detail-editor__number {
  width: 180px;
}

.sale-detail-editor__unit {
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
