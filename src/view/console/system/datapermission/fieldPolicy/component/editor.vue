<script lang="ts" setup>
import { ADialog, AFormField, DialogProps, useEditor } from '@airpower/web'

import { DataFieldPolicyEntity } from '@/model/system/datapermission/DataFieldPolicyEntity'
import { DataFieldPolicyService } from '@/model/system/datapermission/DataFieldPolicyService'

const props = defineProps(DialogProps.withParam(new DataFieldPolicyEntity()))

const {
  formRef,
  formData,
  isLoading,
  onSubmit,
  rules,
  title,
} = useEditor(props, DataFieldPolicyService, {})
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
        只有被 @SensitiveField 标记的字段会被这条策略影响；字段名必须与实体的 JSON 属性名一致，写错等于没配。
        读链路目前只解析 actionName=QUERY 的策略，配 EXPORT 暂不生效（导出侧尚未接线）。
        roleCode 留空或填 default 表示对所有角色兜底，精确角色优先。允许明文＝该角色可看原值（前端也不再遮）。
      </div>
      <AFormField field="resourceId" />
      <AFormField field="fieldName" />
      <AFormField field="roleCode" />
      <AFormField field="actionName" />
      <AFormField field="maskType" />
      <AFormField field="maskExpression" />
      <AFormField field="allowPlaintext" />
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
