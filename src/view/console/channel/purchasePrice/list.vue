<script lang="ts" setup>
import type { MaterialEntity } from '@/model/asset/material/MaterialEntity'
import type { PurchasePriceEntity } from '@/model/channel/purchasePrice/PurchasePriceEntity'
import type { SupplierEntity } from '@/model/channel/supplier/SupplierEntity'
import { ElAlert, ElButton, ElInput, ElLink, ElMessage } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import KFormDialog from '@/component/framework/KFormDialog.vue'
import KPanel from '@/component/framework/KPanel.vue'
import KSelector from '@/component/framework/KSelector.vue'
import KTable from '@/component/framework/KTable.vue'
import { useMaskPolicy } from '@/composables/useMaskPolicy'
import { Permissions } from '@/config/permissions'
import { formatMoney } from '@/framework'
import { KMaterialService } from '@/hook/asset/useMaterial'
import { KPurchasePriceService } from '@/hook/channel/usePurchasePrice'
import { KSupplierService } from '@/hook/channel/useSupplier'
import { useTable } from '@/hook/kubo/useTable'
import SupplierDetail from '../supplier/component/detail.vue'

/**
 * # 采购价列表（F4 批 5 · 新底座）
 *
 * 「物料 × 供应商」唯一的一条专属报价。与旧页面的三处对齐 + 一处改写：
 *
 * | 点 | 旧写法 | 这里 |
 * | --- | --- | --- |
 * | 列表 | `useTanStackTable(PurchasePriceService)` | `useTable(KPurchasePriceService)` |
 * | 编辑 | `editor.vue` + `AFormField` | `KFormDialog` + 两个 `KSelector` 插槽（编辑器文件已删） |
 * | 金额列 | `AMoney` + `#price` 插槽 | `#cell-price` 插槽（同一套遮蔽判据，渲染走 `formatMoney`） |
 * | 供应商 | `APayload :view="SupplierDetail"` | `ElLink` + 同一个供应商详情弹窗（新底座声明式） |
 *
 * **price 必须过脱敏策略**（`@SensitiveField`）：列表列先问 `my-mask` 结论，
 * 受限时显示掩码 —— 不让前端自己判断"该不该遮"（那会多出第二份真相）。
 * 提交侧的拦截在 `beforeSubmit`（见下），两处判据同源。
 */
const pricePolicy = useMaskPolicy('channel.purchasePrice', ['price'])
onMounted(() => pricePolicy.load())

const table = useTable(KPurchasePriceService, {
  queryKey: ['channel:purchasePrice'],
})

const editorVisible = ref(false)
const editingId = ref<number | null>(null)
const form = ref<Record<string, unknown>>({})
const materialPicking = ref(false)
const supplierPicking = ref(false)

const supplierVisible = ref(false)
const supplierId = ref<number | null>(null)

const materialLabel = computed(() => (form.value.material as MaterialEntity | undefined)?.name ?? '')
const supplierLabel = computed(() => (form.value.supplier as SupplierEntity | undefined)?.name ?? '')

function openCreate() {
  editingId.value = null
  editorVisible.value = true
}

function openEdit(row: PurchasePriceEntity) {
  editingId.value = row.id
  editorVisible.value = true
}

function onLoaded(next: Record<string, unknown>) {
  form.value = next
}

function openSupplierDetail(row: PurchasePriceEntity) {
  const id = (row.supplier as SupplierEntity | undefined)?.id
  if (!id) {
    return
  }
  supplierId.value = id
  supplierVisible.value = true
}

function onMaterialPicked(rows: unknown[]) {
  materialPicking.value = false
  const material = rows[0] as MaterialEntity | undefined
  if (!material) {
    return
  }
  form.value.material = material
  if (
    (form.value.price === undefined || form.value.price === null || Number(form.value.price) === 0)
    && material.purchasePrice
  ) {
    form.value.price = material.purchasePrice
  }
}

function onSupplierPicked(rows: unknown[]) {
  supplierPicking.value = false
  const supplier = rows[0] as SupplierEntity | undefined
  if (!supplier) {
    return
  }
  form.value.supplier = supplier
}

/**
 * 提交前守卫：价格被脱敏策略限制时**不许保存**。
 *
 * 数值型敏感字段与手机号不同 —— 后端拿到掩码只会拒绝（置空则会撞 `@NotNull`），
 * 所以前端提前拦住并说清原因，返回 `null` 中止提交（旧版语义完全一致）。
 */
function beforeSubmit(payload: Record<string, unknown>): Record<string, unknown> | null {
  const warning = pricePolicy.blockedSubmit(payload)
  if (warning) {
    ElMessage.warning(warning)
    return null
  }
  return payload
}

function onSaved() {
  table.reload()
}
</script>

<template>
  <div class="k-page">
    <KPanel
      title="采购价"
      description="按「物料 × 供应商」维护的专属采购价；查不到时采购明细回退物料的参考报价"
    >
      <KTable
        :permissions="{
          add: Permissions.Channel.PurchasePrice.add,
          edit: Permissions.Channel.PurchasePrice.update,
          delete: Permissions.Channel.PurchasePrice.delete,
        }"
        :table="table"
        hide-enable
        @add="openCreate"
        @edit="openEdit"
      >
        <template #cell-supplierName="{ row }">
          <ElLink
            :type="(row as PurchasePriceEntity).supplier?.id ? 'primary' : 'info'"
            @click="openSupplierDetail(row as PurchasePriceEntity)"
          >
            {{ (row as PurchasePriceEntity).supplier?.name ?? '-' }}
          </ElLink>
        </template>

        <template #cell-price="{ row }">
          <span v-if="pricePolicy.restricted('price')">
            {{ pricePolicy.masked('price', (row as PurchasePriceEntity).price) }}
          </span>
          <span v-else>{{ formatMoney((row as PurchasePriceEntity).price) }}</span>
        </template>
      </KTable>
    </KPanel>

    <KFormDialog
      :id="editingId"
      v-model="editorVisible"
      :before-submit="beforeSubmit"
      :columns="2"
      :service="KPurchasePriceService"
      title="采购价"
      width="640px"
      @loaded="onLoaded"
      @saved="onSaved"
    >
      <template #field-material="{ field }">
        <div class="picker-field">
          <ElInput
            :model-value="materialLabel"
            :placeholder="`请选择${field.label}`"
            readonly
          />
          <ElButton @click="materialPicking = true">
            选择
          </ElButton>
        </div>
      </template>

      <template #field-supplier="{ field }">
        <div class="picker-field">
          <ElInput
            :model-value="supplierLabel"
            :placeholder="`请选择${field.label}`"
            readonly
          />
          <ElButton @click="supplierPicking = true">
            选择
          </ElButton>
        </div>
      </template>

      <template #extra>
        <ElAlert
          v-if="pricePolicy.maskedFieldsIn(form).length"
          :closable="false"
          show-icon
          type="warning"
        >
          <template #title>
            字段 {{ pricePolicy.maskedFieldsIn(form).join('、') }} 按数据权限策略对你不可见，保持该值无法保存
          </template>
        </ElAlert>
      </template>
    </KFormDialog>

    <KSelector
      v-model="materialPicking"
      :selected="form.material ? [form.material as MaterialEntity] : []"
      :service="KMaterialService"
      title="选择采购物料"
      @confirm="onMaterialPicked"
    />

    <KSelector
      v-model="supplierPicking"
      :selected="form.supplier ? [form.supplier as SupplierEntity] : []"
      :service="KSupplierService"
      title="选择供应商"
      @confirm="onSupplierPicked"
    />

    <SupplierDetail
      :id="supplierId"
      v-model="supplierVisible"
    />
  </div>
</template>

<style lang="scss" scoped>
.k-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
}

.picker-field {
  display: flex;
  gap: 8px;
  width: 100%;
}
</style>
