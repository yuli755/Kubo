<script lang="ts" setup>
import { ElAlert, ElOption, ElSelect } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import KFormDialog from '@/component/framework/KFormDialog.vue'
import { QueryRequest } from '@/framework'
import { KRoleService } from '@/hook/personnel/useRole'
import { DataPolicyService } from '@/hook/system/useDataPolicy'
import { DataResourceService } from '@/hook/system/useDataResource'
import { RoleEntity } from '@/model/personnel/role/RoleEntity'
import { DataResourceEntity } from '@/model/system/datapermission/DataResourceEntity'

/**
 * # 行级策略编辑器（F4 批 6 · 新底座）
 *
 * 字段由 `@KForm` 元数据驱动渲染，并进行如下交互体验优化：
 * 1. **数据资源ID（`resourceId`）下拉速选**：集成 `DataResourceService`，
 *    提供系统已注册数据资源标识与表名下拉，无需手动记忆与查询主键 ID；
 * 2. **角色编码（`roleCode`）下拉速选**：提供 `default`（全员通配）以及全系统角色下拉速选，
 *    支持检索与自定义输入；
 * 3. **维度绑定口径指引**：在 `#extra` 区域清晰呈现维度规则与语法。
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

const resourceList = ref<DataResourceEntity[]>([])
const roleList = ref<RoleEntity[]>([])

async function loadOptions() {
  try {
    const [resources, roles] = await Promise.all([
      new DataResourceService().getList(new QueryRequest(DataResourceEntity)),
      new KRoleService().getList(new QueryRequest(RoleEntity)),
    ])
    resourceList.value = resources
    roleList.value = roles
  }
  catch {
    // 降级兜底
  }
}

onMounted(() => {
  loadOptions()
})

const roleOptions = computed(() => [
  { label: 'default（全员通配 / 默认策略）', value: 'default' },
  ...roleList.value.map(r => ({ label: `${r.name} (${r.code})`, value: r.code })),
])

function close() {
  emit('update:modelValue', false)
}

function onSaved() {
  emit('saved')
  close()
}
</script>

<template>
  <KFormDialog
    :id="id"
    :model-value="modelValue"
    :service="DataPolicyService"
    width="820px"
    @saved="onSaved"
    @update:model-value="close"
  >
    <template #field-resourceId="{ form: f }">
      <ElSelect
        v-model="f.resourceId"
        clearable
        filterable
        placeholder="请选择数据资源"
        style="width: 100%"
      >
        <ElOption
          v-for="res in resourceList"
          :key="res.id"
          :label="`${res.resourceKey}（${res.tableName}）`"
          :value="res.id"
        />
      </ElSelect>
    </template>

    <template #field-roleCode="{ form: f }">
      <ElSelect
        v-model="f.roleCode"
        allow-create
        clearable
        default-first-option
        filterable
        placeholder="请选择角色（留空或 default 为通配）"
        style="width: 100%"
      >
        <ElOption
          v-for="role in roleOptions"
          :key="role.value"
          :label="role.label"
          :value="role.value"
        />
      </ElSelect>
    </template>

    <template #extra>
      <ElAlert
        class="policy-tip"
        type="info"
        :closable="false"
        show-icon
        title="维度绑定怎么写"
      >
        工厂（FACTORY）维度已落地：先在「用户」上维护其所属工厂（user_factory 关系表），
        登录/刷新会把工厂节点连同整棵仓库子树展开进 factoryIds，行级与写侧共用同一份 factoryIds。
        策略列绑定沿 <code>FACTORY:storage.id</code> 关联路径走一跳（当前接在 wms.inventory / wms.move）。
        租户 / 用户 / 部门 / 仓库 / 角色同样可用。<code>conditionJson</code> 只允许白名单列，写裸 SQL 会被拒绝。
      </ElAlert>
    </template>
  </KFormDialog>
</template>

<style scoped>
.policy-tip {
  margin-top: 8px;
}
</style>
