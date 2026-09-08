<script lang="ts" setup>
import { APanel, ATable } from '@airpower/web'
import { ElAlert } from 'element-plus'
import { usePolicyResourceRiskReport } from '@/composables/usePolicyResourceRiskReport'
import { useRowPolicyRiskReport } from '@/composables/useRowPolicyRiskReport'
import { Permissions } from '@/config/permissions'
import { useTanStackTable } from '@/hook/useTanStackTable'
import { DataPolicyService } from '@/model/system/datapermission/DataPolicyService'
import { DataPolicyEditor } from './component'

// 行级"永不命中"盘点：判定与原因都在后端，这里只列出来
const { count: riskCount, preview: riskPreview, failed: riskFailed } = useRowPolicyRiskReport()

// 策略指向缺失/未启用资源盘点：资源引用坏掉，运行期永远轮不到它。判定与原因都在后端
const {
  count: refRiskCount,
  preview: refRiskPreview,
  failed: refRiskFailed,
} = usePolicyResourceRiskReport()

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
      title="有启用的行级策略无法按预期生效"
      :description="`${riskCount} 条（${riskPreview.join('、')}）。行级规则不命中意味着该主体在这个资源上完全没有过滤；而如果它是该主体的唯一策略，enforce 后会落到默认拒绝、该主体查不到数据。请按类别修正动作名与主体必填项，FACTORY 一类留到工厂维度接入后再配；EXPLICIT_DENY 一类（CREATE 配了显式拒绝）请直接删除该策略——白名单下「禁止创建」的正确表达是不配 CREATE 策略。`"
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
    <ElAlert
      v-if="refRiskCount"
      class="risk-warning"
      type="warning"
      :closable="false"
      show-icon
      title="有启用的行级策略引用了不存在或未启用的资源"
      :description="`${refRiskCount} 条（${refRiskPreview.join('、')}）。这类策略运行期永远轮不到、界面却显示已启用：资源已删除的请删除该策略，资源只是未启用的请到「数据资源」启用后再谈放开。`"
    />
    <ElAlert
      v-else-if="refRiskFailed"
      class="risk-warning"
      type="info"
      :closable="false"
      show-icon
      title="未能取得策略资源引用盘点结果"
      description="刷新本页或检查 /dataResource/policy-resource-risks 是否可用；取不到结果时不会显示为「没有风险」。"
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
