<script lang="ts" setup>
import type { FormInstance } from 'element-plus'

import { ADialog, AInput, DialogProps, DialogUtil } from '@airpower/web'
import { ElMessage } from 'element-plus'
import { ref } from 'vue'
import { InputDetailService, useInputAddFinish } from '@/hook/wms/useInput'
import { InputDetailEntity } from '@/model/wms/input/InputDetailEntity'
import { StorageSelector } from '@/view/console/factory/storage/component'

const props = defineProps(DialogProps.withParam(new InputDetailEntity()))

const formData = ref(props.param.copy())

const isLoading = ref(false)

const formRef = ref<FormInstance>()

const { mutateAsync: addFinishMutate } = useInputAddFinish()

async function onSubmit() {
  const form = formData.value.copy()
  // 阶段 E-H1：后端 InputService.afterDetailFinishAdded 从载荷读取 storage.id（入库目标）
  // 与 quantity（本次累加数量）。旧写法传 finishQuantity（绑定到实体历史完成量字段）
  // 且不带仓库，「确认入库」必然 403「请传入入库仓库」。
  if (!form.storage || !form.storage.id) {
    ElMessage.warning('请选择目标仓库')
    return
  }
  await addFinishMutate({
    id: form.id,
    quantity: form.quantity,
    storage: { id: form.storage.id },
  })
  props.onConfirm()
}

const quantity = ref(formData.value.quantity - formData.value.finishQuantity)
const material = ref(formData.value.material.copy())
formData.value.quantity = quantity.value
formData.value.expose('id', 'quantity')

async function selectStorage() {
  formData.value.storage = await DialogUtil.show(StorageSelector)
  formData.value.storageName = formData.value.storage.name
  formData.value.storageId = formData.value.storage.id
}
</script>

<template>
  <ADialog
    :disable-confirm="formData.quantity <= 0"
    :form-ref="formRef"
    :loading="isLoading"
    title="确认入库"
    width="600px"
    @cancel="onCancel"
    @confirm="onSubmit"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="InputDetailService.createValidator()"
      label-width="120px"
      @submit.prevent
    >
      <el-form-item label="物料信息">
        <el-input
          v-model="material.name"
          disabled
        />
      </el-form-item>
      <el-form-item
        label="目标仓库"
        prop="storageId"
      >
        <AInput
          v-model="formData.storageName"
          :entity="InputDetailEntity"
          placeholder="请选择目标仓库..."
          @click="selectStorage"
        />
      </el-form-item>
      <el-form-item label="待入数量">
        <el-input
          v-model="quantity"
          disabled
        >
          <template #append>
            {{ material.unit.name }}
          </template>
        </el-input>
      </el-form-item>
      <el-form-item label="入库数量">
        <el-input
          v-model="formData.quantity"
          type="number"
        >
          <template #append>
            {{ material.unit.name }}
          </template>
        </el-input>
      </el-form-item>
    </el-form>
  </ADialog>
</template>
