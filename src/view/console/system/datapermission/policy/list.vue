<script lang="ts" setup>
import { ElAlert } from 'element-plus'
import { ref } from 'vue'
import KPanel from '@/component/framework/KPanel.vue'
import KTable from '@/component/framework/KTable.vue'
import { usePolicyResourceRiskReport } from '@/composables/usePolicyResourceRiskReport'
import { useRowPolicyRiskReport } from '@/composables/useRowPolicyRiskReport'
import { Permissions } from '@/config/permissions'
import { useTable } from '@/hook/kubo/useTable'
import { DataPolicyService } from '@/hook/system/useDataPolicy'
import DataPolicyEditor from './component/editor.vue'

// 行级"永不命中"盘点：判定与原因都在后端，这里只列出来
const { count: riskCount, preview: riskPreview, failed: riskFailed } = useRowPolicyRiskReport()

// 策略指向缺失/未启用资源盘点：资源引用坏掉，运行期永远轮不到它。判定与原因都在后端
const {
  count: refRiskCount,
  preview: refRiskPreview,
  failed: refRiskFailed,
} = usePolicyResourceRiskReport()

// 列表与编辑器：页面自己开弹窗（与其它新底座页面同一写法）
const table = useTable(DataPolicyService, { queryKey: ['system:datapermission:policy'] })

const editorVisible = ref(false)
const editingId = ref<number | null>(null)

function openCreate() {
  editingId.value = null
  editorVisible.value = true
}

function openEdit(row: { id: number }) {
  editingId.value = row.id
  editorVisible.value = true
}

function onSaved() {
  table.reload()
}
</script>

<template>
  <KPanel>
    <ElAlert
      v-if="riskCount"
      class="risk-warning"
      type="error"
      :closable="false"
      show-icon
      title="有启用的行级策略无法按预期生效"
      :description="`${riskCount} 条（${riskPreview.join('、')}）。行级规则不命中意味着该主体在这个资源上完全没有过滤；而如果它是该主体的唯一策略，enforce 后会落到默认拒绝、该主体查不到数据。请按类别修正动作名与主体必填项，EXPLICIT_DENY 一类（CREATE 配了显式拒绝）请直接删除该策略——白名单下「禁止创建」的正确表达是不配 CREATE 策略。`"
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
    <KTable
      :table="table"
      :permissions="{
        add: Permissions.System.DataPolicy.add,
        edit: Permissions.System.DataPolicy.update,
        delete: Permissions.System.DataPolicy.delete,
      }"
      @add="openCreate"
      @edit="openEdit"
    />

    <DataPolicyEditor
      :id="editingId"
      v-model="editorVisible"
      @saved="onSaved"
    />
  </KPanel>
</template>

<style lang="scss" scoped>
.risk-warning {
  margin-bottom: 8px;
}
</style>
