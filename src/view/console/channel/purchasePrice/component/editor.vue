<script lang="ts" setup>
import { ADialog, AFormField, ASelect, DialogProps, useEditor } from '@airpower/web'
import { ElMessage } from 'element-plus'
import { onMounted } from 'vue'
import { useMaskPolicy } from '@/composables/useMaskPolicy'

import { PurchasePriceEntity } from '@/model/channel/purchasePrice/PurchasePriceEntity'
import { PurchasePriceService } from '@/model/channel/purchasePrice/PurchasePriceService'
import { MaterialSelector } from '@/view/console/asset/material/component'
import { SupplierSelector } from '../../supplier/component'

const props = defineProps(DialogProps.withParam(new PurchasePriceEntity()))

// price 在本模块被 @SensitiveField 标记：受限角色从详情接口拿到的就是 "****"，
// 而表单是"整实体回填再整实体提交"的。数值列没法像 phone 那样由服务端用库中原值兜底
// （置 null 会被实体的 @NotNull(groups = WhenUpdate) 报成"采购单价不能为空"），
// 后端只能拒绝掩码 —— 所以这里在点保存时把原因说清楚，而不是丢一个看不懂的 400。
const pricePolicy = useMaskPolicy('channel.purchasePrice', ['price'])

onMounted(() => pricePolicy.load())

const { maskedFieldsIn } = pricePolicy

const {
  title,
  formData,
  rules,
  formRef,
  isLoading,
  onSubmit,
} = useEditor(props, PurchasePriceService, {
  beforeSubmit(submitData) {
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
        label="供应商"
        prop="supplier"
      >
        <ASelect
          v-model="formData.supplier"
          :selector="SupplierSelector"
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
