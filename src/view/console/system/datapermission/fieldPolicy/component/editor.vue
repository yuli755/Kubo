<script lang="ts" setup>
import { ADialog, AFormField, DialogProps, useEditor } from '@airpower/web'
import { ElAlert } from 'element-plus'
import { computed } from 'vue'
import { gapsForField, useExportGapReport } from '@/composables/useExportGapReport'

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

/**
 * 与当前这条策略同字段的导出缺口（按 resourceId + fieldName 筛，判定仍在后端）
 *
 * 为什么放在编辑器里而不是只留在资源列表页：补漏的动作发生在这里 —— 管理员正在写这条策略，
 * 此刻是"该不该顺手补一条 EXPORT 明文"唯一的决策窗口。列表页的汇总只是入口，
 * 真到编辑时还得切页面去回忆是哪个字段，等于没有。
 *
 * 这里必须写 `formData.value.*`：`useEditor` 返回的是 `Ref<Entity>`（模板里自动解包，
 * script 里不解包 —— 这一点不是推测，是 vue-tsc 在本轮 CI 上撞出来的：
 * "Property 'resourceId' does not exist on type 'Ref<DataFieldPolicyEntity, …>'"）。
 * 既然 `.value` 本身就是响应式依赖，computed 不会读到过期值，无需退回每次渲染重算。
 */
const { items: gapItems, failed: gapsUnavailable } = useExportGapReport()

const fieldGaps = computed(() => gapsForField(gapItems, formData.value.resourceId, formData.value.fieldName))
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
      <ElAlert
        v-if="fieldGaps.length"
        class="gap-hint"
        type="warning"
        :closable="false"
        show-icon
        :title="`这个字段还有 ${fieldGaps.length} 处导出缺口`"
        :description="fieldGaps.map(g => `在 ${g.resourceKey}：${g.reason}`).join('；')"
      />
      <ElAlert
        v-else-if="gapsUnavailable"
        class="gap-hint"
        type="info"
        :closable="false"
        show-icon
        title="未能取得导出缺口自查结果"
        description="不影响保存这条策略；但此刻看不到缺口，别把它读成「没有缺口」。"
      />
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
.gap-hint {
  margin-bottom: 8px;
}

.tip {
  padding: 0 0 8px 150px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}
</style>
