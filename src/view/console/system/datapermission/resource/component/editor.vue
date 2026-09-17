<script lang="ts" setup>
import { ElAlert } from 'element-plus'
import KFormDialog from '@/component/framework/KFormDialog.vue'
import { DataResourceService } from '@/hook/system/useDataResource'

/**
 * # 数据资源编辑器（F4 批 6 · 新底座）
 *
 * 字段全部来自 `@KForm` 元数据；页面额外挂两块**口径说明**（`#extra`）：
 *
 * 1. **资源标识必须与后端 `data_resource.resource_key` 一致**：不一致时前端引用它的
 *    权限码会被 `perms:check` 拦下；租户列/归属列/可过滤列共同决定策略能引用哪些列，
 *    不在白名单里的条件树保存时被拒（判据在后端 `DataPolicyService`，页面不重复实现）；
 * 2. **归属列的维度语法**：`DEPARTMENT:departmentId` / `WAREHOUSE:storage.id`
 *    这类写法只有一份解析器（后端 `OwnerColumnRegistration`），页面照抄规则而不是自建校验 ——
 *    否则就会出现"界面说合法、保存被拒"的两套判据。
 */
defineProps<{
  /** 显隐 */
  modelValue: boolean
  /** 编辑的记录 id（不传为新增） */
  id?: number | null
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'saved'): void
}>()
</script>

<template>
  <KFormDialog
    :id="id"
    :model-value="modelValue"
    :columns="2"
    :service="DataResourceService"
    width="900px"
    @closed="emit('update:modelValue', false)"
    @saved="emit('saved')"
    @update:model-value="value => emit('update:modelValue', value)"
  >
    <template #extra>
      <ElAlert
        class="resource-tip"
        type="info"
        :closable="false"
        show-icon
        title="资源注册"
      >
        资源标识必须与后端 <code>data_resource.resource_key</code> 一致；未注册的资源，
        前端引用它的权限码会被 perms:check 拦下。租户列 / 归属列 / 可过滤列共同决定了策略能引用哪些列 ——
        不在白名单里的条件树会在保存时被拒绝（判据在后端，界面不重复实现）。
        「支持的动作」不参与权限判定：列级遮蔽只认 QUERY / EXPORT，行级策略按策略行自己的动作匹配；
        目前只有开发期数据初始化（DevDataInitRunner）读它，用来决定要不要给这个资源生成示例策略。
      </ElAlert>
      <ElAlert
        class="resource-tip"
        type="info"
        :closable="false"
        show-icon
        title="归属列可以按维度登记"
      >
        语法只有一份（后端 OwnerColumnRegistration 解析）：<code>creatorId</code> 是负责人列（裸写法＝今天的语义）；
        <code>DEPARTMENT:departmentId</code> 给部门单独立一项，负责人档与部门档就不再抢同一列；
        <code>WAREHOUSE:storage.id</code> 沿关联取列，最多一跳，更深请把中间列冗余出来。多项用逗号分隔。
        保存时后端会校验这一串（非法写法直接被拒），改完仍需在「维度绑定风险」面板确认这一档绑得上。
      </ElAlert>
    </template>
  </KFormDialog>
</template>

<style scoped>
.resource-tip {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.6;
}
</style>
