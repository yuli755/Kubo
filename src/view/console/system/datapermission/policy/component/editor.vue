<script lang="ts" setup>
import { ADialog, AFormField, DialogProps, useEditor } from '@airpower/web'

import { DataPolicyService } from '@/hook/system/useDataPolicy'
import { DataPolicyEntity } from '@/model/system/datapermission/DataPolicyEntity'

const props = defineProps(DialogProps.withParam(new DataPolicyEntity()))

const {
  formRef,
  formData,
  isLoading,
  onSubmit,
  rules,
  title,
} = useEditor(props, DataPolicyService, {})
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
        工厂（FACTORY）维度尚未落地：没有 user_factory 关系表、factoryIds 恒为空，保存会被后端 DimensionGuard 拒绝；
        条件树里引用 factoryIds 同样被拒（那会被渲染成恒假条件，整个列表变空结果）。
        租户 / 用户 / 部门 / 仓库 / 角色可用。conditionJson 只允许白名单列，写裸 SQL 会被拒绝。
      </div>
      <AFormField field="policyCode" />
      <AFormField field="resourceId" />
      <AFormField field="subjectType" />
      <AFormField field="subjectId" />
      <AFormField field="roleCode" />
      <AFormField field="actionName" />
      <AFormField field="conditionJson" />
      <AFormField field="priority" />
      <AFormField field="enabled" />
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
