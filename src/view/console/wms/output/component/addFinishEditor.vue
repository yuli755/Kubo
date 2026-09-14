<script lang="ts" setup>
import type { FormInstance } from 'element-plus'
import { ADialog, AInput, DialogProps, DialogUtil } from '@airpower/web'
import { ElMessage } from 'element-plus'
import { ref } from 'vue'
import { InventoryEntity } from '@/model/wms/inventory/InventoryEntity'
import { InventoryTypeEnum } from '@/model/wms/inventory/InventoryTypeEnum'

import { OutputDetailService, useOutputAddFinish } from '@/hook/wms/useOutput'
import { OutputDetailEntity } from '@/model/wms/output/OutputDetailEntity'
import { InventorySelector } from '../../inventory/component'

const props = defineProps(DialogProps.withParam(new OutputDetailEntity()))

const formData = ref(props.param.copy())

const isLoading = ref(false)

const formRef = ref<FormInstance>()

const { mutateAsync: addFinishMutate } = useOutputAddFinish()

async function onSubmit() {
  const form = formData.value.copy()
  // 阶段 E-H1：后端 OutputService.afterDetailFinishAdded 从载荷读取 inventory.id（扣减来源）
  // 与 quantity（本次累加数量）。旧写法传 finishQuantity 且不带库存，「确认出库」必然失败。
  if (!form.inventory || !form.inventory.id) {
    ElMessage.warning('请选择库存来源')
    return
  }
  await addFinishMutate({
    id: form.id,
    quantity: form.quantity,
    inventory: { id: form.inventory.id },
  })
  props.onConfirm()
}

const quantity = ref(formData.value.quantity - formData.value.finishQuantity)
const material = ref(formData.value.material.copy())
// 默认填「待出数量」，与入库确认编辑器对齐：部分出库后再次确认时，
// 默认值若仍是明细总量会直接触发「完成数量不得超过单据数量」。
formData.value.quantity = quantity.value
formData.value.expose('id', 'quantity')

async function selectInventory() {
  let inventory = new InventoryEntity()
  inventory.type = InventoryTypeEnum.STORAGE.key
  inventory.material = material.value.copy()
  inventory = await DialogUtil.show(InventorySelector, inventory)
  formData.value.inventory = inventory.copy()
  formData.value.inventoryId = formData.value.inventory.id
  formData.value.storageName = formData.value.inventory.storage.name
  formData.value.quantity = Math.min(
    formData.value.inventory.quantity,
    quantity.value,
  )
}
</script>

<template>
  <ADialog
    :disable-confirm="formData.quantity <= 0"
    :form-ref="formRef"
    :loading="isLoading"
    title="确认出库"
    width="600px"
    @cancel="onCancel"
    @confirm="onSubmit"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="OutputDetailService.createValidator()"
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
        label="库存来源"
        prop="inventoryId"
      >
        <AInput
          v-model="formData.storageName"
          :entity="OutputDetailEntity"
          placeholder="请选择库存来源..."
          @click="selectInventory"
        />
      </el-form-item>
      <el-form-item
        v-if="formData.inventory"
        label="当前库存"
      >
        <el-input
          v-model="formData.inventory.quantity"
          disabled
        >
          <template #append>
            {{ material.unit.name }}
          </template>
        </el-input>
      </el-form-item>
      <el-form-item label="待出数量">
        <el-input
          v-model="quantity"
          disabled
        >
          <template #append>
            {{ material.unit.name }}
          </template>
        </el-input>
      </el-form-item>
      <el-form-item label="出库数量">
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

<style lang="sass" scoped></style>
