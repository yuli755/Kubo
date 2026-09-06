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
        actionName=QUERY 决定界面与详情是否遮蔽；actionName=EXPORT 决定导出能否带该列明文 ——
        受限角色导出时，被 QUERY 遮蔽的每一个字段都要有一条 EXPORT 且「允许明文＝是」，缺一条整个导出被拒
        （导出是一整份文件，不部分放行）。roleCode 留空或填 default 表示对所有角色兜底，精确角色优先。
        后端 kubo.data-permission.mode=shadow 时只记 export-bypass 账、不拦；enforce 才拦。
        动作下拉里 QUERY 与 EXPORT 是两条独立策略：只配 QUERY 会得到「界面遮蔽 + 导出被拒」，这是预期行为，不是漏配。
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
