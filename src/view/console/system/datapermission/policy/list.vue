<script lang="ts" setup>
import { APanel, ATable } from '@airpower/web'
import { ElAlert } from 'element-plus'
import { useRowPolicyRiskReport } from '@/composables/useRowPolicyRiskReport'
import { Permissions } from '@/config/permissions'
import { useTanStackTable } from '@/hook/useTanStackTable'
import { DataPolicyService } from '@/model/system/datapermission/DataPolicyService'
import { DataPolicyEditor } from './component'

// 行级"永不命中"盘点：判定与原因都在后端，这里只列出来
const { count: riskCount, preview: riskPreview, failed: riskFailed } = useRowPolicyRiskReport()

const hook = useTanStackTable(DataPolicyService, {
  queryKey: ['system:datapermission:policy'],
  editView: DataPolicyEditor,
})
</script>

<template>
  <APanel>
    <ElAlert
      v-if="riskCount"
      class="risk-warning"
      type="error"
      :closable="false"
      show-icon
      title="有启用的行级策略永远不会被任何请求读到"
      :description="`${riskCount} 条（${riskPreview.join('、')}）。行级规则不命中意味着该主体在这个资源上完全没有过滤；而如果它是该主体的唯一策略，enforce 后会落到默认拒绝、该主体查不到数据。请按类别修正动作名与主体必填项，FACTORY 一类留到工厂维度接入后再配。`"
    />
    <ElAlert
      v-else-if="riskFailed"
      class="risk-warning"
      type="info"
      :closable="false"
      show-icon
      title="未能取得行级策略盘点结果"
      description="刷新本页或检查 /dataResource/row-policy-risks 是否可用；取不到结果时不会显示为「没有风险」。"
    />
    <ATable
      :use-hook="hook"
      :add-permission="Permissions.System.DataPolicy.add"
      :delete-permission="Permissions.System.DataPolicy.delete"
      :edit-permission="Permissions.System.DataPolicy.update"
    />
  </APanel>
</template>

<style lang="scss" scoped>
.risk-warning {
  margin-bottom: 8px;
}
</style>
