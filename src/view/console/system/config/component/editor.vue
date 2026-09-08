<script lang="ts" setup>
import { ADialog, AFormField, DialogProps, getFieldLabel, useEditor } from '@airpower/web'
import { computed } from 'vue'

import { Constant } from '@/config/Constant'
import { ConfigService } from '@/hook/system/useConfig'
import { ConfigEntity } from '@/model/system/config/ConfigEntity'
import { ConfigType } from '@/model/system/config/ConfigType'

const props = defineProps(DialogProps.withParam(new ConfigEntity()))

const {
  title,
  formData,
  rules,
  formRef,
  isLoading,
  onSubmit,
} = useEditor(props, ConfigService, {
  afterGetDetail: (data) => {
    switch (data.type) {
      case ConfigType.NUMBER.key:
        data.config = Number(data.config) as unknown as string
        break
      case ConfigType.BOOLEAN.key:
        data.config = (data.config === Constant.BOOLEAN_YES) as unknown as string
        break
      default:
    }
    return data
  },

  beforeSubmit: (data) => {
    switch (data.type) {
      case ConfigType.NUMBER.key:
        data.config = data.config.toString()
        break
      case ConfigType.BOOLEAN.key:
        data.config = data.config ? Constant.BOOLEAN_YES : Constant.BOOLEAN_NO
        break
      default:
    }
    return data
  },
})

/** el-input-number 需要 number 类型，但 ConfigEntity.config 是 string */
const numberConfig = computed({
  get: () => (formData.value ? Number(formData.value.config) : 0),
  set: (val: number) => {
    if (formData.value)
      formData.value.config = String(val) as unknown as string
  },
})

/** el-switch 需要 boolean 类型，但 ConfigEntity.config 是 string */
const booleanConfig = computed({
  get: () => formData.value?.config === Constant.BOOLEAN_YES,
  set: (val: boolean) => {
    if (formData.value)
      formData.value.config = (val ? Constant.BOOLEAN_YES : Constant.BOOLEAN_NO) as unknown as string
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
      label-width="140px"
      @submit.prevent
    >
      <AFormField
        :disabled="formData.isSystem"
        field="flag"
      />
      <AFormField field="name" />
      <AFormField
        :disabled="formData.isSystem"
        field="type"
      />
      <template v-if="ConfigType.NUMBER.equalsKey(formData.type)">
        <el-form-item :label="getFieldLabel(ConfigEntity, 'config')">
          <el-input-number v-model="numberConfig" />
        </el-form-item>
      </template>
      <template v-else-if="ConfigType.BOOLEAN.equalsKey(formData.type)">
        <el-form-item :label="getFieldLabel(ConfigEntity, 'config')">
          <el-switch v-model="booleanConfig" />
        </el-form-item>
      </template>

      <template v-else>
        <el-form-item :label="getFieldLabel(ConfigEntity, 'config')">
          <el-input v-model="formData.config" />
        </el-form-item>
      </template>
      <el-form-item :label="getFieldLabel(ConfigEntity, 'description')">
        <el-input
          v-model="formData.description"
          disabled
          type="textarea"
        />
      </el-form-item>
    </el-form>
  </ADialog>
</template>

<style lang="scss" scoped>
.param-item {
  margin: 0 2px;
  cursor: pointer;
}
</style>
