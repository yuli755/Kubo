<script lang="ts" setup>
import { APanel, ATable } from '@airpower/web'
import { ElAlert } from 'element-plus'
import { useDataPermissionRuntimeStatus } from '@/composables/useDataPermissionRuntimeStatus'
import { useDimensionBindingRiskReport } from '@/composables/useDimensionBindingRiskReport'
import { useEnforceReadiness } from '@/composables/useEnforceReadiness'
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

// 灰度状态：档位与"名单里有没有它"都由后端算（effectiveMode 只有一份），这里只负责显示
const {
  ready: runtimeReady,
  failed: runtimeFailed,
  summary: runtimeSummary,
  pendingCount: runtimePendingCount,
  notePreview: runtimeNotePreview,
  resources: runtimeResources,
  unmanaged: runtimeUnmanaged,
} = useDataPermissionRuntimeStatus()

// 放开前置检查：ready / blockers 都是后端算好的（影子证据那条判据前端根本无从复现），这里只数条数
const {
  failed: readinessFailed,
  total: readinessTotal,
  readyCount: readinessReadyCount,
  preview: readinessPreview,
} = useEnforceReadiness()

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
      :description="`${bindingCount} 处（${bindingPreview.join('、')}）：这一档在 enforce 下会变成「该主体查不到任何数据」，shadow 下则只记审计。请到本页编辑该资源的 tenantColumn / ownerColumns：裸写法照旧，要区分档位或沿关联取列时写成 DEPARTMENT:departmentId、WAREHOUSE:storage.id；报表名找不到的条目要先补资源登记。`"
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
    <ElAlert
      v-if="runtimeReady"
      class="gap-warning"
      type="info"
      :closable="false"
      show-icon
      title="数据权限灰度状态（档位由后端给出，本页不参与判定）"
      :description="runtimeSummary"
    />
    <ElAlert
      v-if="runtimeReady && runtimePendingCount"
      class="gap-warning"
      type="warning"
      :closable="false"
      show-icon
      title="有资源的灰度档位与策略数量不匹配"
      :description="`${runtimePendingCount} 个资源有待处理提示（${runtimeNotePreview.join('；')}）。提示是后端算好的，放开前按它走完那一步。`"
    />
    <ElAlert
      v-if="runtimeReady && runtimeUnmanaged.length"
      class="gap-warning"
      type="warning"
      :closable="false"
      show-icon
      title="有服务能解析出资源键、但「数据资源」里没有登记"
      :description="`${runtimeUnmanaged.join('、')}。未登记时写侧是否放行取决于后端 deny-unregistered，本页不重复判；先把资源登记出来，再谈放开灰度。`"
    />
    <ElAlert
      v-if="runtimeFailed"
      class="gap-warning"
      type="info"
      :closable="false"
      show-icon
      title="未能取得灰度状态总览"
      description="刷新本页或检查 /dataResource/runtime-status 是否可用；取不到结果时不会显示为「一切正常」。"
    />
    <section
      v-if="runtimeReady"
      class="parity-panel"
      aria-label="ORM 对账分布"
    >
      <div class="parity-panel__title">
        ORM 对账分布（后端已按资源统计，未登记探针不等于通过）
      </div>
      <div
        v-for="item in runtimeResources"
        :key="item.resourceKey"
        class="parity-panel__row"
      >
        <strong>{{ item.resourceKey }}</strong>
        <span
          v-if="!item.ormParityRequired"
          class="parity-panel__muted"
        >
          未进入 Mapper 换轨范围
        </span>
        <span
          v-else-if="!item.ormParityTableAvailable"
          class="parity-panel__danger"
        >
          缺证据：对账表不可读
        </span>
        <span v-else>
          一致 {{ item.ormParityConsistentCount }} · Mapper 少放行 {{ item.ormParityStricterCount }} ·
          Mapper 多放行 {{ item.ormParityLooserCount }} · 错误 {{ item.ormParityErrorCount }}
        </span>
      </div>
    </section>
    <ElAlert
      v-if="readinessFailed"
      class="gap-warning"
      type="info"
      :closable="false"
      show-icon
      title="未能取得放开前置检查结果"
      description="刷新本页或检查 /dataResource/enforce-readiness 是否可用；取不到结果时不会显示为「都可以放开」。"
    />
    <ElAlert
      v-else-if="readinessTotal && readinessReadyCount < readinessTotal"
      class="gap-warning"
      type="warning"
      :closable="false"
      show-icon
      title="还有资源不满足 enforce 放开前置"
      :description="`${readinessTotal - readinessReadyCount} 个资源未就绪（${readinessPreview.join('；')}）。检查项与结论由后端给出，本页不参与判定；缺的补完再放开，一次只放一个。`"
    />
    <ElAlert
      v-else-if="readinessTotal"
      class="gap-warning"
      type="success"
      :closable="false"
      show-icon
      title="已登记资源都满足放开前置"
      description="全绿只说明「可以试放一个」：放开后请回本页与审计页复核，一次只放一个资源。"
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

.parity-panel {
  margin-bottom: 8px;
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  background: var(--el-fill-color-blank);
  color: var(--el-text-color-regular);
}

.parity-panel__title {
  margin-bottom: 8px;
  font-weight: 600;
}

.parity-panel__row {
  display: flex;
  gap: 12px;
  align-items: baseline;
  padding: 4px 0;
  line-height: 1.5;
}

.parity-panel__muted {
  color: var(--el-text-color-secondary);
}

.parity-panel__danger {
  color: var(--el-color-danger);
}
</style>
