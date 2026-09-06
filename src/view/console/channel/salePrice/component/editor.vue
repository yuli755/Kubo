<script lang="ts" setup>
import { ADialog, AFormField, ASelect, DialogProps, useEditor } from '@airpower/web'
import { ElMessage } from 'element-plus'
import { onMounted } from 'vue'
import { useMaskPolicy } from '@/composables/useMaskPolicy'

import { SalePriceEntity } from '@/model/channel/salePrice/SalePriceEntity'
import { SalePriceService } from '@/model/channel/salePrice/SalePriceService'
import { MaterialSelector } from '@/view/console/asset/material/component'
import { CustomerSelector } from '../../customer/component'

const props = defineProps(DialogProps.withParam(new SalePriceEntity()))

// 与采购价同一套约束：price 是数值型敏感字段，受限时后端只会拒绝掩码、不会兜底恢复，
// 所以提示与拦截都放在提交前（口径见 useMaskPolicy.blockedSubmit 的注释）。
const pricePolicy = useMaskPolicy('channel.salePrice', ['price'])

onMounted(() => pricePolicy.load())

const { maskedFieldsIn } = pricePolicy

const {
  title,
  formData,
  rules,
  formRef,
  isLoading,
  onSubmit,
} = useEditor(props, SalePriceService, {
  afterGetDetail(detailData) {
    detailData.customerName = detailData.customer.name
    detailData.materialId = detailData.material.id
    detailData.materialName = detailData.material.name
    return detailData
  },
  beforeSubmit(submitData) {
    submitData.exclude('materialId', 'customerId')
    const warning = pricePolicy.blockedSubmit(submitData)
    if (warning) {
      ElMessage.warning(warning)
      return null // 框架语义：beforeSubmit 返回 null 即中止提交
    }
    return submitData
  },
})
</script>

<template>
  <ADialog
    :form-ref="formRef"
    :loading="isLoading"
    :title="title"
    @cancel="onCancel"
    @confirm="onSubmit"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      label-width="120px"
      @submit.prevent
    >
      <el-form-item
        label="采购物料"
        prop="material"
      >
        <ASelect
          v-model="formData.material"
          :selector="MaterialSelector"
        />
      </el-form-item>
      <el-form-item
        label="客户"
        prop="customer"
      >
        <ASelect
          v-model="formData.customer"
          :selector="CustomerSelector"
        />
      </el-form-item>
      <el-alert
        v-if="maskedFieldsIn(formData).length"
        :closable="false"
        show-icon
        type="warning"
      >
        <template #title>
          字段 {{ maskedFieldsIn(formData).join('、') }} 按数据权限策略对你不可见，保持该值无法保存
        </template>
      </el-alert>
      <AFormField field="price" />
    </el-form>
  </ADialog>
</template>
