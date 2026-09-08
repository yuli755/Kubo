<script lang="ts" setup>
import { ADialog, AFormField, DialogProps, getFormConfigList, useEditor } from '@airpower/web'
import { CustomerService } from '@/hook/channel/useCustomer'
import { CustomerEntity } from '@/model/channel/customer/CustomerEntity'

const props = defineProps(DialogProps.withParam(new CustomerEntity()))

const {
  title,
  formData,
  rules,
  formRef,
  isLoading,
  onSubmit,
} = useEditor(props, CustomerService)
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
      <AFormField
        v-for="item in getFormConfigList(CustomerEntity)"
        :key="item.key"
        :field="item.key"
      />
    </el-form>
  </ADialog>
</template>
