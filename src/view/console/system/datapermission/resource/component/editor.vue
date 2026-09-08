<script lang="ts" setup>
import { ADialog, AFormField, DialogProps, useEditor } from '@airpower/web'

import { DataResourceService } from '@/hook/system/useDataResource'
import { DataResourceEntity } from '@/model/system/datapermission/DataResourceEntity'

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
      <div class="tip">
        归属列可以按维度登记（语法只有一份，后端 OwnerColumnRegistration 解析，这里照抄不重述规则）：
        `creatorId` 是负责人列（裸写法＝今天的语义，存量登记不用改）；`DEPARTMENT:departmentId` 给部门
        单独立一项，负责人档与部门档就不再抢同一列；`WAREHOUSE:storage.id` 沿 @ManyToOne 关联取列，
        最多一跳，更深请把中间列冗余出来。多项用逗号分隔。
        保存时后端会校验这一串（非法写法直接被拒），改完仍需在「维度绑定风险」面板确认这一档绑得上；
        校验规则只有一份，别把这里当校验器用。
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
