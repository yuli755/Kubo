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
        租户列 / 归属列 / 可过滤列共同决定了策略能引用哪些列——不在白名单里的条件树会在保存时被拒绝
        （`DataPolicyService` 构造字段白名单，越界直接 PARAM_INVALID，这条是真的）。
        「支持的动作」不参与权限判定：列级遮蔽只认 QUERY / EXPORT，行级策略按策略行自己的动作匹配，
        两者都不读这一列。目前只有开发期数据初始化（DevDataInitRunner）读它，用来决定要不要给这个资源
        生成示例策略 —— 所以从这里删掉某个动作既不会关闭能力、也不会让已有策略失效，只影响下次初始化。
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
