<script lang="ts" setup>
import { ADialog, AFormField, AGroup, DialogProps, useEditor } from '@airpower/web'
import { OrderDetailEntity } from '@/model/mes/order/OrderDetailEntity'
import { OrderDetailService } from '@/model/mes/order/OrderDetailService'

const props = defineProps(DialogProps.withParam(new OrderDetailEntity()))

const {
  title,
  formData,
  rules,
  formRef,
  isLoading,
  onSubmit,
} = useEditor(props, OrderDetailService)
</script>

<template>
  <ADialog
    :loading="isLoading"
    :title="title"
    width="500px"
    @cancel="onCancel"
    @confirm="onSubmit"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      label-width="100px"
      @submit.prevent
    >
      <AGroup :column="1">
        <AFormField
          v-model="formData"
          :entity="OrderDetailEntity"
          field="material"
        />
        <AFormField
          v-model="formData"
          :entity="OrderDetailEntity"
          field="quantity"
        />
      </AGroup>
    </el-form>
  </ADialog>
</template>
