<script lang="ts" setup>
import { ADialog, AFormField, ASelect, DialogProps, useEditor } from '@airpower/web'
import { ElMessage } from 'element-plus'
import { onMounted } from 'vue'
import { useMaskPolicy } from '@/composables/useMaskPolicy'
import { MaterialService } from '@/hook/asset/useMaterial'

import { MaterialEntity } from '@/model/asset/material/MaterialEntity'
import { UnitSelector } from '@/view/console/system/unit/component'

const props = defineProps(DialogProps.withParam(new MaterialEntity()))

// 物料上的两个标准价都是数值型敏感字段：受限角色拿到的是掩码，而这里没有"绕过该字段保存"
// 的安全写法（整实体提交下删字段等于写 null，实体的 @NotNull 会报成"不能为空"），
// 所以后端拒绝、前端提前说明。列表侧的遮蔽由 v-mask / desensitize 负责，与此无关。
const pricePolicy = useMaskPolicy('asset.material', ['purchasePrice', 'salePrice'])

onMounted(() => pricePolicy.load())

const { maskedFieldsIn } = pricePolicy

const {
  title,
  formData,
  rules,
  formRef,
  isLoading,
  onSubmit,
} = useEditor(props, MaterialService, {
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
      <AFormField field="name" />
      <AFormField field="code" />
      <AFormField field="materialType" />
      <AFormField field="useType" />
      <AFormField field="spc" />
      <el-form-item
        label="计量单位"
        prop="unit"
      >
        <ASelect
          v-model="formData.unit"
          :selector="UnitSelector"
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
      <AFormField field="purchasePrice" />
      <AFormField field="salePrice" />
    </el-form>
  </ADialog>
</template>
