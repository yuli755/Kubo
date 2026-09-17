<script lang="ts" setup>
import { ElAlert } from 'element-plus'
import KFormDialog from '@/component/framework/KFormDialog.vue'
import { DataPolicyService } from '@/hook/system/useDataPolicy'

/**
 * # 行级策略编辑器（F4 批 6 · 新底座）
 *
 * 九个字段全部由 `@KForm` 元数据驱动渲染，页面只负责一件事：**把维度绑定的口径写在
 * 用户眼前**（`#extra` 里那段说明）。
 *
 * 那段文案不是装饰：`conditionJson` 只允许白名单列、工厂维度要沿
 * `FACTORY:storage.id` 关联一跳、裸 SQL 会被后端拒绝 —— 这些规则**只在后端实现**，
 * 界面上不说，管理员就会把"保存成功但永不命中"的策略当成生效。
 * 放在 `#extra`（字段之后）：先填再读到解释，比先读一段长文再填更符合操作顺序。
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
    @update:model-value="close"
    @saved="onSaved"
  >
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
