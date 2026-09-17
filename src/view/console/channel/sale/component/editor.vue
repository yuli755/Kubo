<script lang="ts" setup>
import type { CustomerEntity } from '@/model/channel/customer/CustomerEntity'
import type { SaleDetailEntity } from '@/model/channel/sale/SaleDetailEntity'
import { ElButton, ElInput } from 'element-plus'
import { computed, ref } from 'vue'
import KDetailTable from '@/component/framework/KDetailTable.vue'
import KFormDialog from '@/component/framework/KFormDialog.vue'
import KSelector from '@/component/framework/KSelector.vue'
import { Permissions } from '@/config/permissions'
import { DialogUtil, FeedbackUtil } from '@/framework'
import { KCustomerService } from '@/hook/channel/useCustomer'
import { KSaleService } from '@/hook/channel/useSale'
import { SaleDetailEntity as SaleDetail } from '@/model/channel/sale/SaleDetailEntity'
import SaleDetailEditor from './detailEditor.vue'

/**
 * # 销售单编辑器（F4 批 5 · 新底座）
 *
 * 与采购单编辑器同构（主表单元数据渲染 + 明细 `KDetailTable`），差别只有两处：
 *
 * 1. **客户是单据主表字段**：旧版用 `ASelect + CustomerSelector` 选客户，这里用
 *    `KSelector + KCustomerService` 写进 `#field-customerId` 插槽，选中时同时回填
 *    `customer`（嵌套对象）与 `customerId`（后端关联用）—— 提交前再兜一次同步，
 *    保证两个字段永远一致（旧版 `afterGetDetail` 里手工补 customerId 就是为这件事）；
 * 2. **明细继承单据客户**：新建明细时把表单上的客户塞进明细，明细编辑器据此查
 *    「该物料 × 该客户」的专属销售价。
 *
 * 顺手修掉的两处旧文案：分组标题「采购单」→「销售单」、删明细确认「采购明细」→「销售明细」
 * （旧页面复制采购单改的，一直没改回来）。
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
const details = computed<SaleDetailEntity[]>(() => (form.value.details ?? []) as SaleDetailEntity[])
const customerPicking = ref(false)

const customerName = computed(() => (form.value.customer as CustomerEntity | undefined)?.name ?? '')

function onLoaded(next: Record<string, unknown>) {
  next.details ??= []
  form.value = next
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

    <template #extra>
      <div class="sale-editor__details">
        <div class="sale-editor__head">
          <span class="sale-editor__title">销售明细</span>
          <ElButton
            v-permission="[Permissions.Channel.Sale.add, Permissions.Channel.Sale.update]"
            link
            type="primary"
            @click="addDetail"
          >
            添加明细
          </ElButton>
        </div>

        <KDetailTable
          :entity="SaleDetail"
          :exclude="['createTime', 'updateTime']"
          :row-actions="[{
            label: '删除',
            danger: true,
            permission: [Permissions.Channel.Sale.add, Permissions.Channel.Sale.update],
            handler: removeDetail,
          }]"
          :rows="details"
          empty-text="还没有明细，点「添加明细」选一条物料"
        />
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
</template>

<style lang="scss" scoped>
.sale-editor__customer {
  display: flex;
  gap: 8px;
  width: 100%;
}

.sale-editor__details {
  margin-top: 8px;
}

.sale-editor__head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.sale-editor__title {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>
