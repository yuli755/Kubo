<script lang="ts" setup>
import { APanel, ATable } from '@airpower/web'
import { ElAlert } from 'element-plus'
import { useExportGapReport } from '@/composables/useExportGapReport'
import { Permissions } from '@/config/permissions'
import { useTanStackTable } from '@/hook/useTanStackTable'
import { DataResourceService } from '@/model/system/datapermission/DataResourceService'
import { DataResourceEditor } from './component'

// 导出授权缺口自查：后端只回结论，这里连"哪算缺口"都不判（判据在 FieldPolicyGapAnalyzer）
const { summary: gapSummary, failed: gapFailed } = useExportGapReport()

const hook = useTanStackTable(DataResourceService, {
  queryKey: ['system:datapermission:resource'],
  editView: DataResourceEditor,
})
</script>

<template>
  <APanel>
    <ElAlert
      v-if="gapSummary.entries"
      class="gap-warning"
      type="warning"
      :closable="false"
      show-icon
      title="有资源配了列级遮蔽、却没配导出明文授权"
      :description="`${gapSummary.entries} 处缺口分布在 ${gapSummary.resources} 个资源（${gapSummary.preview.join('、')}）。enforce 下这些资源的受限角色一旦导出即整份被拒；请到「列级脱敏策略」补 actionName=EXPORT 且允许明文的策略。`"
    />
    <ElAlert
      v-else-if="gapFailed"
      class="gap-warning"
      type="info"
      :closable="false"
      show-icon
      title="未能取得导出授权自查结果"
      description="刷新本页或检查 /dataResource/export-gaps 是否可用；取不到结果时不会显示为「没有缺口」。"
    />
    <ATable
      :use-hook="hook"
      :add-permission="Permissions.System.DataResource.add"
      :delete-permission="Permissions.System.DataResource.delete"
      :edit-permission="Permissions.System.DataResource.update"
    />
  </APanel>
</template>

<style lang="scss" scoped>
.gap-warning {
  margin-bottom: 8px;
}
</style>
