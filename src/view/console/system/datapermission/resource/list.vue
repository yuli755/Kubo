<script lang="ts" setup>
import { APanel, ATable } from '@airpower/web'
import { ElAlert } from 'element-plus'
import { useDimensionBindingRiskReport } from '@/composables/useDimensionBindingRiskReport'
import { useExportGapReport } from '@/composables/useExportGapReport'
import { useInertPolicyReport } from '@/composables/useInertPolicyReport'
import { Permissions } from '@/config/permissions'
import { useTanStackTable } from '@/hook/useTanStackTable'
import { DataResourceService } from '@/model/system/datapermission/DataResourceService'
import { DataResourceEditor } from './component'

// 两个独立自查：缺什么（要补）与有什么多余的（要清）。分开取数，一个失败不牵连另一个。
const { count: inertCount } = useInertPolicyReport()

// 导出授权缺口自查：后端只回结论，这里连"哪算缺口"都不判（判据在 FieldPolicyGapAnalyzer）
const { summary: gapSummary, failed: gapFailed } = useExportGapReport()

// 维度可绑定性：列级/行级的判据都不在这里，页面只负责把后端数出来的条数与摘要摆出来
const { count: bindingCount, preview: bindingPreview, failed: bindingFailed } = useDimensionBindingRiskReport()

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
    <ElAlert
      v-if="inertCount"
      class="gap-warning"
      type="info"
      :closable="false"
      show-icon
      title="另有动作不会被列级遮蔽读到的存量策略"
      :description="`${inertCount} 条：到「列级脱敏策略」页可逐条查看，停用或改到行级即可，不用留在表里充数。`"
    />
    <ElAlert
      v-if="bindingCount"
      class="gap-warning"
      type="warning"
      :closable="false"
      show-icon
      title="有资源的行级策略找不到可绑的列"
      :description="`${bindingCount} 处（${bindingPreview.join('、')}）：这一档在 enforce 下会变成「该主体查不到任何数据」，shadow 下则只记审计。请到本页编辑该资源的 tenantColumn / ownerColumns，让它对上实体上真实存在的属性名；报表名找不到的条目要先补资源登记。`"
    />
    <ElAlert
      v-else-if="bindingFailed"
      class="gap-warning"
      type="info"
      :closable="false"
      show-icon
      title="未能取得维度可绑定性盘点结果"
      description="刷新本页或检查 /dataResource/dimension-binding-risks 是否可用；取不到结果时不会显示为「没有风险」。"
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
