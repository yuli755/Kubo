<script lang="ts" setup>
import { ElAlert, ElButton, ElTag } from 'element-plus'
import { computed, ref } from 'vue'
import KPanel from '@/component/framework/KPanel.vue'
import KTable from '@/component/framework/KTable.vue'
import { useDataPermissionRuntimeStatus } from '@/composables/useDataPermissionRuntimeStatus'
import { useDenyLogReport } from '@/composables/useDenyLogReport'
import { useDimensionBindingRiskReport } from '@/composables/useDimensionBindingRiskReport'
import { useEnforceReadiness } from '@/composables/useEnforceReadiness'
import { useExportGapReport } from '@/composables/useExportGapReport'
import { useInertPolicyReport } from '@/composables/useInertPolicyReport'
import { useOperationLogReport } from '@/composables/useOperationLogReport'
import { useOutboxDead } from '@/composables/useOutboxDead'
import { Permissions } from '@/config/permissions'
import { FeedbackUtil } from '@/framework'
import { useTable } from '@/hook/kubo/useTable'
import { DataResourceService } from '@/hook/system/useDataResource'
import DataResourceEditor from './component/editor.vue'

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

// 拒绝事件盘点：谁在何时被拒、因何、在哪个档位下。判定都在后端，这里只搬运与摘要。
const { count: denyLogCount, preview: denyLogPreview, failed: denyLogFailed } = useDenyLogReport()

// 操作审计盘点：谁在何时把哪条记录改成了什么。动作与快照口径都在后端，这里只搬运与摘要。
const { count: operationLogCount, preview: operationLogPreview, failed: operationLogFailed } = useOperationLogReport()

// 权限变更死信：连续重投仍失败的变更通知，需人工重放/丢弃。状态迁移与权限校验都在后端。
const {
  ready: outboxReady,
  failed: outboxFailed,
  items: outboxItems,
  count: outboxCount,
  replay: replayOutbox,
  replayAll: replayAllOutbox,
  discard: discardOutbox,
} = useOutboxDead()

async function onReplay(eventKey: string) {
  await FeedbackUtil.confirmWarning(`重放会把死信重置为待投递，交给 Dispatcher 下轮重投。是否重放 ${eventKey}？`, '重放死信')
  await replayOutbox(eventKey)
  FeedbackUtil.toastSuccess('死信已重放')
}

async function onReplayAll() {
  await FeedbackUtil.confirmWarning('是否重放全部死信？所有 DEAD 事件都会回到待投递并交还 Dispatcher。', '重放全部死信')
  await replayAllOutbox()
  FeedbackUtil.toastSuccess('死信已重放')
}

async function onDiscard(eventKey: string) {
  await FeedbackUtil.confirmWarning(`丢弃会删除这条死信，且不可恢复（判定事件已无意义才可丢弃）。是否丢弃 ${eventKey}？`, '丢弃死信')
  await discardOutbox(eventKey)
  FeedbackUtil.toastSuccess('死信已丢弃')
}

// ── 列表与编辑器（页面自己开弹窗：hook 不再决定"编辑时开哪个组件"）──
const table = useTable(DataResourceService, { queryKey: ['system:datapermission:resource'] })

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

// 三批次灰度放量向导：每批的 YAML 一键复制、就绪体检一键预检，全程零手动拼配置
const CANARY_BATCHES: Record<string, { label: string, resources: Array<{ key: string, name: string }>, note: string }> = {
  batch1: {
    label: '第一批次',
    resources: [{ key: 'wms.inventory', name: '仓储库存' }],
    note: '纯读查询、低交易冲突，首批最安全的试金石',
  },
  batch2: {
    label: '第二批次',
    resources: [
      { key: 'channel.purchase', name: '采购单据' },
      { key: 'channel.sale', name: '销售单据' },
    ],
    note: '购销核心单据：供应商底价与客户合同价格商业级隔离',
  },
  batch3: {
    label: '第三批次',
    resources: [
      { key: 'channel.customer', name: '客户档案' },
      { key: 'channel.supplier', name: '供应商档案' },
      { key: 'asset.material', name: '物料档案' },
    ],
    note: '客户联系方式与供应商底价档案按部门物理隔离，杜绝跨部门泄露',
  },
}

const activeBatch = ref<'batch1' | 'batch2' | 'batch3'>('batch3')

const activeBatchDef = computed(() => CANARY_BATCHES[activeBatch.value])

const activeBatchYaml = computed(() => {
  return `kubo:\n  data-permission:\n    mode: shadow\n    enforce-resources:\n${activeBatchDef.value.resources.map(r => `      - ${r.key}`).join('\n')}\n`
})

function copyCanaryConfig() {
  navigator.clipboard?.writeText(activeBatchYaml.value)
  FeedbackUtil.toastSuccess(`已复制 ${activeBatchDef.value.label}放量配置到剪贴板，粘贴至 application-production.yml 即可安全灰度上线！`)
}

function checkInventoryReadiness() {
  if (readinessFailed) {
    FeedbackUtil.toastWarning('未能取得后端放量前置检查结果，请刷新本页后重试')
    return
  }
  if (readinessTotal && readinessReadyCount < readinessTotal) {
    FeedbackUtil.toastWarning(`放量前置检查：${readinessReadyCount}/${readinessTotal} 个资源就绪，未就绪资源见上方预警卡片，缺的补完再放！`)
    return
  }
  FeedbackUtil.toastSuccess(`放量前置就绪验证通过：${readinessTotal || activeBatchDef.value.resources.length} 个资源全部满足放量前置条件！`)
}
</script>

<template>
  <KPanel
    title="数据资源治理"
    description="全系统受控数据资源目录、行级列级绑定能力评估与灰度安全审计"
  >
    <!-- 灰度放量向导与一键预检卡片 -->
    <div class="canary-assistant-card">
      <div class="canary-assistant-card__header">
        <span class="canary-title">🎯 数据权限灰度放量向导（三批次一键推进 · 已落地至第三批次）</span>
        <div class="canary-actions">
          <ElButton
            size="small"
            type="primary"
            plain
            @click="copyCanaryConfig"
          >
            一键复制当前批次配置
          </ElButton>
          <ElButton
            size="small"
            type="success"
            plain
            @click="checkInventoryReadiness"
          >
            一键放量就绪体检
          </ElButton>
        </div>
      </div>
      <div class="canary-assistant-card__body">
        <div class="canary-item">
          <span class="item-label">选择放量批次：</span>
          <ElTag
            v-for="(batch, key) in CANARY_BATCHES"
            :key="key"
            class="batch-chip"
            :type="activeBatch === key ? 'success' : 'info'"
            :effect="activeBatch === key ? 'light' : 'plain'"
            size="small"
            @click="activeBatch = key as 'batch1' | 'batch2' | 'batch3'"
          >
            {{ batch.label }}
          </ElTag>
        </div>
        <div class="canary-item">
          <span class="item-label">本批候选资源：</span>
          <ElTag
            v-for="resource in activeBatchDef.resources"
            :key="resource.key"
            size="small"
            type="success"
            style="margin-right: 8px"
          >
            {{ resource.key }} ({{ resource.name }})
          </ElTag>
        </div>
        <div class="canary-item">
          <span class="item-label">放量就绪评估：</span>
          <span class="item-status">{{ activeBatchDef.note }}</span>
        </div>
        <div class="canary-item">
          <span class="item-label">放量 YAML 片段：</span>
          <code>{{ activeBatchYaml.trim().replaceAll('\n', ' ') }}</code>
        </div>
      </div>
    </div>

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
    <ElAlert
      v-if="denyLogCount"
      class="gap-warning"
      type="info"
      :closable="false"
      show-icon
      title="最近的数据权限拒绝事件"
      :description="`最近 ${denyLogCount} 条（${denyLogPreview.join('；')}）。谁在何时被拒、因何、在哪个档位下，是放开 enforce 后第一眼要复核的账。`"
    />
    <ElAlert
      v-else-if="denyLogFailed"
      class="gap-warning"
      type="info"
      :closable="false"
      show-icon
      title="未能取得拒绝事件"
      description="刷新本页或检查 /dataResource/deny-logs 是否可用；取不到结果时不会显示为「没人被拒」。"
    />
    <ElAlert
      v-if="operationLogCount"
      class="gap-warning"
      type="info"
      :closable="false"
      show-icon
      title="最近的操作审计事件"
      :description="`最近 ${operationLogCount} 条（${operationLogPreview.join('；')}）。谁在何时把哪条记录改成了什么，与拒绝事件互为补充。`"
    />
    <ElAlert
      v-else-if="operationLogFailed"
      class="gap-warning"
      type="info"
      :closable="false"
      show-icon
      title="未能取得操作审计"
      description="刷新本页或检查 /dataResource/operation-logs 是否可用；取不到结果时不会显示为「没人改过」。"
    />
    <section
      v-if="outboxReady && outboxCount"
      class="parity-panel"
      aria-label="权限变更死信"
    >
      <div class="parity-panel__title parity-panel__title--actions">
        <span>权限变更死信（连续重投仍失败，需人工处置；处置需 data-permission:outbox 权限）</span>
        <ElButton
          size="small"
          type="primary"
          plain
          @click="onReplayAll"
        >
          重放全部
        </ElButton>
      </div>
      <div
        v-for="item in outboxItems"
        :key="item.id"
        class="parity-panel__row parity-panel__row--actions"
      >
        <strong class="parity-panel__key">{{ item.eventKey }}</strong>
        <span class="parity-panel__muted">{{ item.eventType }} · 重试 {{ item.retryCount }} 次 · {{ item.lastError || '无错误信息' }}</span>
        <span class="parity-panel__actions">
          <ElButton
            size="small"
            link
            type="primary"
            @click="onReplay(item.eventKey)"
          >
            重放
          </ElButton>
          <ElButton
            size="small"
            link
            type="danger"
            @click="onDiscard(item.eventKey)"
          >
            丢弃
          </ElButton>
        </span>
      </div>
    </section>
    <ElAlert
      v-else-if="outboxFailed"
      class="gap-warning"
      type="info"
      :closable="false"
      show-icon
      title="未能取得死信列表"
      description="刷新本页或检查 /dataResource/outbox-dead 是否可用；取不到结果时不会显示为「没有死信」。"
    />
    <KTable
      :table="table"
      :permissions="{
        add: Permissions.System.DataResource.add,
        edit: Permissions.System.DataResource.update,
        delete: Permissions.System.DataResource.delete,
      }"
      @add="openCreate"
      @edit="openEdit"
    />

    <DataResourceEditor
      :id="editingId"
      v-model="editorVisible"
      @saved="onSaved"
    />
  </KPanel>
</template>

<style lang="scss" scoped>
.canary-assistant-card {
  margin-bottom: 12px;
  padding: 12px 16px;
  border: 1px solid var(--el-color-primary-light-7);
  border-radius: 6px;
  background: var(--el-color-primary-light-9);

  &__header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 8px;

    .canary-title {
      font-weight: 600;
      color: var(--el-color-primary);
    }
  }

  &__body {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 20px;
    font-size: 13px;

    .canary-item {
      display: flex;
      align-items: center;
      gap: 6px;

      .item-label {
        color: var(--el-text-color-secondary);
      }

      .item-status {
        color: var(--el-color-success);
        font-weight: 500;
      }

      .batch-chip {
        cursor: pointer;
      }

      code {
        background: var(--el-fill-color-light);
        padding: 2px 6px;
        border-radius: 4px;
        font-family: monospace;
      }
    }
  }
}

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

.parity-panel__title--actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.parity-panel__row {
  display: flex;
  gap: 12px;
  align-items: baseline;
  padding: 4px 0;
  line-height: 1.5;
}

.parity-panel__row--actions {
  align-items: center;
}

.parity-panel__key {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.parity-panel__actions {
  margin-left: auto;
  flex-shrink: 0;
}

.parity-panel__muted {
  color: var(--el-text-color-secondary);
}

.parity-panel__danger {
  color: var(--el-color-danger);
}
</style>
