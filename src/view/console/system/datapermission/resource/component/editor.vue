<script lang="ts" setup>
import { ADialog, AFormField, DialogProps, useEditor } from '@airpower/web'

import { DataResourceEntity } from '@/model/system/datapermission/DataResourceEntity'
import { DataResourceService } from '@/model/system/datapermission/DataResourceService'

const props = defineProps(DialogProps.withParam(new DataResourceEntity()))

const {
  formRef,
  formData,
  isLoading,
  onSubmit,
  rules,
  title,
} = useEditor(props, DataResourceService, {})
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
      label-width="150px"
      @submit.prevent
    >
      <div class="tip">
        资源标识必须与后端 data_resource.resource_key 一致；未注册的资源，前端引用它的权限码会被 perms:check 拦下。
        租户列 / 归属列 / 可过滤列共同决定了策略能引用哪些列——不在白名单里的条件树会在保存时被拒绝。
      </div>
      <AFormField field="resourceKey" />
      <AFormField field="tableName" />
      <AFormField field="tableAlias" />
      <AFormField field="tenantColumn" />
      <AFormField field="ownerColumns" />
      <AFormField field="filterableColumns" />
      <AFormField field="supportedActions" />
      <AFormField field="status" />
    </el-form>
  </ADialog>
</template>

<style lang="scss" scoped>
.tip {
  padding: 0 0 8px 150px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}
</style>
