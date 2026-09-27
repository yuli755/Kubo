<script lang="ts" setup>
import type { MaterialEntity } from '@/model/asset/material/MaterialEntity'
import type { CustomerEntity } from '@/model/channel/customer/CustomerEntity'
import type { SalePriceEntity } from '@/model/channel/salePrice/SalePriceEntity'
import { ElAlert, ElButton, ElInput, ElMessage } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import KFormDialog from '@/component/framework/KFormDialog.vue'
import KPanel from '@/component/framework/KPanel.vue'
import KSelector from '@/component/framework/KSelector.vue'
import KTable from '@/component/framework/KTable.vue'
import { useMaskPolicy } from '@/composables/useMaskPolicy'
import { Permissions } from '@/config/permissions'
import { formatMoney } from '@/framework'
import { KMaterialService } from '@/hook/asset/useMaterial'
import { KCustomerService } from '@/hook/channel/useCustomer'
import { KSalePriceService } from '@/hook/channel/useSalePrice'
import { useTable } from '@/hook/kubo/useTable'

/**
 * # 销售价列表（F4 批 5 · 新底座）
 *
 * 「物料 × 客户」唯一的一条专属销售价，是采购价的镜像页（同一套脱敏守卫、同一个
 * `KFormDialog` + 两个 `KSelector` 插槽的写法）。
 *
 * 迁移时修掉旧版 `afterGetDetail` / `beforeSubmit` 里两处别扭的补丁：
 * - 旧版回填时手工写 `customerName` / `materialId` / `materialName` 三个扁平字段 ——
 *   新底座的列渲染读嵌套对象（`@KTable` 的 formatter），这三个字段不再需要维护；
 * - 旧版 `beforeSubmit` 先 `exclude('materialId','customerId')` 再提交 ——
 *   这里改为**不生成**这两个字段（实体上它们本来就没有 K 表单元数据），
 *   少一个"提交前删字段"的隐藏动作。
 */
const pricePolicy = useMaskPolicy('channel.salePrice', ['price'])
onMounted(() => pricePolicy.load())

const table = useTable(KSalePriceService, {
  queryKey: ['channel:salePrice'],
})

const editorVisible = ref(false)
const editingId = ref<number | null>(null)
const form = ref<Record<string, unknown>>({})
const materialPicking = ref(false)
const customerPicking = ref(false)

const materialLabel = computed(() => (form.value.material as MaterialEntity | undefined)?.name ?? '')
const customerLabel = computed(() => (form.value.customer as CustomerEntity | undefined)?.name ?? '')

function openCreate() {
  editingId.value = null
  editorVisible.value = true
}

function openEdit(row: SalePriceEntity) {
  editingId.value = row.id
  editorVisible.value = true
}

function onLoaded(next: Record<string, unknown>) {
  form.value = next
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
    && material.salePrice
  ) {
    form.value.price = material.salePrice
  }
}

function onCustomerPicked(rows: unknown[]) {
  customerPicking.value = false
  const customer = rows[0] as CustomerEntity | undefined
  if (!customer) {
    return
  }
  form.value.customer = customer
}

/** 提交前守卫：与采购价同源（`blockedSubmit` 说清原因，返回 `null` 中止） */
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
      title="销售价"
      description="按「物料 × 客户」维护的专属销售价；查不到时销售明细回退物料的参考报价"
    >
      <KTable
        :permissions="{
          add: Permissions.Channel.SalePrice.add,
          edit: Permissions.Channel.SalePrice.update,
          delete: Permissions.Channel.SalePrice.delete,
        }"
        :table="table"
        hide-enable
        @add="openCreate"
        @edit="openEdit"
      >
        <template #cell-price="{ row }">
          <span v-if="pricePolicy.restricted('price')">
            {{ pricePolicy.masked('price', (row as SalePriceEntity).price) }}
          </span>
          <span v-else>{{ formatMoney((row as SalePriceEntity).price) }}</span>
        </template>
      </KTable>
    </KPanel>

    <KFormDialog
      :id="editingId"
      v-model="editorVisible"
      :before-submit="beforeSubmit"
      :columns="2"
      :service="KSalePriceService"
      title="销售价"
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

      <template #field-customer="{ field }">
        <div class="picker-field">
          <ElInput
            :model-value="customerLabel"
            :placeholder="`请选择${field.label}`"
            readonly
          />
          <ElButton @click="customerPicking = true">
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
      title="选择销售物料"
      @confirm="onMaterialPicked"
    />

    <KSelector
      v-model="customerPicking"
      :selected="form.customer ? [form.customer as CustomerEntity] : []"
      :service="KCustomerService"
      title="选择销售客户"
      @confirm="onCustomerPicked"
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
