<script lang="ts" setup>
import type { EChartsType } from 'echarts/core'
import type { ResourceRuntimeStatus } from '@/api/system/types'
import { RefreshRight } from '@element-plus/icons-vue'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import Skeleton from '@/component/Skeleton.vue'
import { useDataPermissionRuntimeStatus } from '@/composables/useDataPermissionRuntimeStatus'
import { useDenyLogReport } from '@/composables/useDenyLogReport'
import { useDimensionBindingRiskReport } from '@/composables/useDimensionBindingRiskReport'
import { useEnforceReadiness } from '@/composables/useEnforceReadiness'
import { useExportGapReport } from '@/composables/useExportGapReport'
import { useInertPolicyReport } from '@/composables/useInertPolicyReport'
import { useOutboxDead } from '@/composables/useOutboxDead'
import { usePolicyResourceRiskReport } from '@/composables/usePolicyResourceRiskReport'
import { useRowPolicyRiskReport } from '@/composables/useRowPolicyRiskReport'

/**
 * # 数据权限安全态势看板（P2 · 前端二次改造）
 *
 * ## 它只做一件事：把 9 份既有的盘点汇成一处可见
 * 这里**不新增任何判定**：enforce 进度、SHADOW 对账、拒绝事件、导出缺口、惰性策略、
 * 维度绑定、行级/资源风险、Outbox 死信，全部来自后端已算好的结论
 * （`dataResource/*` 管理端点）。前端若在这里再算一遍「什么算缺口 / 什么算风险」，
 * 就是抄第二份真相 —— 后端一改判据，看板立刻开始说谎。
 *
 * ## 失败取向与数据来源
 * 每个 composable 都遵循「失败只置 failed，不假装安全」：取不到时显示「未能取得」，
 * 绝不渲染成「没有问题」。这些端点由后端鉴权（跨租户的策略元数据），
 * 因此本组件只挂载在持有数据权限管理身份的页面里（见 `index.vue` 的 `v-if`）。
 *
 * ## 图表
 * 两张图都只聚合 `runtimeStatus.resources` 里后端给出的字段，不做任何语义推断：
 * - 档位分布（OFF / SHADOW / ENFORCE 资源数）—— 展示灰度进展；
 * - ORM 对账分布（一致 / 偏严 / 偏松 / 错误）—— 展示 shadow 证据质量，偏松是泄露方向。
 */

const runtime = useDataPermissionRuntimeStatus()
const readiness = useEnforceReadiness()
const deny = useDenyLogReport()
const gap = useExportGapReport()
const inert = useInertPolicyReport()
const binding = useDimensionBindingRiskReport()
const rowPolicy = useRowPolicyRiskReport()
const policyResource = usePolicyResourceRiskReport()
const outbox = useOutboxDead()

interface StatCard {
  /** 卡片标题 */
  label: string
  /** 主数字；取不到时由模板渲染为「未能取得」 */
  value: number | string
  /** 是否取数失败（决定卡片是否退化为告警态） */
  failed: boolean
  /** 首条人话摘要；空则显示「无」 */
  detail: string
  /** 命中数是否是需要关注的「问题」：true 用告警色，false 用常态色 */
  alerting: boolean
}

/**
 * 归一化后的卡片序列：数字与摘要都来自后端结论，这里只决定「怎么摆」。
 */
const cards = computed<StatCard[]>(() => [
  {
    label: '放开就绪',
    value: readiness.fetched ? `${readiness.readyCount} / ${readiness.total}` : '—',
    failed: readiness.failed,
    detail: readiness.preview[0] ?? '全部已登记资源满足放开前置',
    alerting: readiness.fetched && readiness.readyCount < readiness.total,
  },
  {
    label: '拒绝事件',
    value: deny.count,
    failed: deny.failed,
    detail: deny.preview[0] ?? '最近无数据权限拒绝事件',
    alerting: deny.count > 0,
  },
  {
    label: '导出缺口',
    value: gap.summary.entries,
    failed: gap.failed,
    detail: gap.summary.preview[0] ?? '无导出授权缺口',
    alerting: gap.summary.entries > 0,
  },
  {
    label: '惰性列级策略',
    value: inert.count,
    failed: inert.failed,
    detail: inert.preview[0] ?? '无存量惰性列级策略',
    alerting: inert.count > 0,
  },
  {
    label: '维度绑定风险',
    value: binding.count,
    failed: binding.failed,
    detail: binding.preview[0] ?? '无可绑定性风险',
    alerting: binding.count > 0,
  },
  {
    label: '行级策略风险',
    value: rowPolicy.count,
    failed: rowPolicy.failed,
    detail: rowPolicy.preview[0] ?? '无行级策略风险',
    alerting: rowPolicy.count > 0,
  },
  {
    label: '策略资源风险',
    value: policyResource.count,
    failed: policyResource.failed,
    detail: policyResource.preview[0] ?? '无资源引用风险',
    alerting: policyResource.count > 0,
  },
  {
    label: 'Outbox 死信',
    value: outbox.count,
    failed: outbox.failed,
    detail: outbox.preview[0] ?? '无待处置死信',
    alerting: outbox.count > 0,
  },
])

/**
 * ORM 对账分布：跨资源累加后端给的四个计数器，不自行判定方向语义。
 */
const ormParity = computed(() => {
  let consistent = 0
  let stricter = 0
  let looser = 0
  let error = 0
  for (const resource of runtime.resources) {
    consistent += resource.ormParityConsistentCount
    stricter += resource.ormParityStricterCount
    looser += resource.ormParityLooserCount
    error += resource.ormParityErrorCount
  }
  return { consistent, stricter, looser, error }
})

const modeChartEl = ref<HTMLDivElement>()
const parityChartEl = ref<HTMLDivElement>()
let modeChart: EChartsType | undefined
let parityChart: EChartsType | undefined

async function renderCharts() {
  if (!runtime.ready)
    return
  if (runtime.resources.length === 0)
    return
  // 动态按需加载 ECharts，避免全量打入主包
  const echarts = await import('@/utils/echarts')
  await nextTick()
  if (modeChartEl.value && !modeChart) {
    modeChart = echarts.init(modeChartEl.value)
  }
  if (parityChartEl.value && !parityChart) {
    parityChart = echarts.init(parityChartEl.value)
  }
  modeChart?.setOption(buildModeOption(runtime.resources))
  parityChart?.setOption(buildParityOption())
}

function buildModeOption(resources: ResourceRuntimeStatus[]) {
  const modes = ['OFF', 'SHADOW', 'ENFORCE'] as const
  const data = modes.map(mode => ({
    name: mode,
    value: resources.filter(r => r.effectiveMode === mode).length,
  })).filter(item => item.value > 0)
  return {
    tooltip: { trigger: 'item' as const },
    legend: { bottom: 0 },
    series: [
      {
        name: '资源档位',
        type: 'pie' as const,
        radius: ['38%', '68%'],
        center: ['50%', '45%'],
        avoidLabelOverlap: true,
        label: { formatter: '{b}: {c}' },
        data,
      },
    ],
  }
}

function buildParityOption() {
  const { consistent, stricter, looser, error } = ormParity.value
  return {
    tooltip: { trigger: 'axis' as const },
    legend: { bottom: 0 },
    grid: { left: '8%', right: '4%', top: '8%', bottom: '22%', containLabel: true },
    xAxis: {
      type: 'category' as const,
      data: ['一致', '偏严', '偏松', '错误'],
    },
    yAxis: { type: 'value' as const, minInterval: 1 },
    series: [
      {
        name: '对账样本数',
        type: 'bar' as const,
        data: [
          { value: consistent, itemStyle: { color: '#67c23a' } },
          { value: stricter, itemStyle: { color: '#909399' } },
          { value: looser, itemStyle: { color: '#f56c6c' } },
          { value: error, itemStyle: { color: '#e6a23c' } },
        ],
        barMaxWidth: 60,
      },
    ],
  }
}

async function refreshAll() {
  await Promise.all([
    runtime.refresh(),
    readiness.refresh(),
    deny.refresh(),
    gap.refresh(),
    inert.refresh(),
    binding.refresh(),
    rowPolicy.refresh(),
    policyResource.refresh(),
    outbox.refresh(),
  ])
}

// 数据就绪后渲染图表；手动刷新会替换 resources 数组，也需要重绘
watch(
  () => [runtime.ready, runtime.resources.length],
  () => {
    if (runtime.ready)
      void renderCharts()
  },
)

onMounted(() => {
  if (runtime.ready)
    void renderCharts()
})

onBeforeUnmount(() => {
  modeChart?.dispose()
  parityChart?.dispose()
  modeChart = undefined
  parityChart = undefined
})
</script>

<template>
  <section class="security-dashboard">
    <div class="dashboard-header">
      <h3 class="title">
        数据权限安全态势
      </h3>
      <el-button
        :loading="runtime.loading"
        :icon="RefreshRight"
        circle
        @click="refreshAll"
      />
    </div>

    <el-alert
      v-if="runtime.failed"
      class="summary-alert"
      type="warning"
      :closable="false"
      show-icon
      title="未能取得灰度状态总览"
      description="取不到结果时不会显示为「一切正常」；请检查 /dataResource/runtime-status 是否可用。"
    />
    <el-alert
      v-else-if="runtime.ready"
      class="summary-alert"
      type="info"
      :closable="false"
      show-icon
      :title="runtime.summary"
    />

    <div class="cards">
      <el-card
        v-for="card in cards"
        :key="card.label"
        class="stat-card"
        shadow="never"
      >
        <div class="stat-label">
          {{ card.label }}
        </div>
        <div
          class="stat-value"
          :class="card.alerting ? 'alert' : 'normal'"
        >
          {{ card.failed ? '未能取得' : card.value }}
        </div>
        <div class="stat-detail">
          {{ card.failed ? '取数失败，请刷新或检查接口' : card.detail }}
        </div>
      </el-card>
    </div>

    <div class="charts">
      <el-card
        class="chart-card"
        shadow="never"
      >
        <template #header>
          <span class="chart-title">资源档位分布</span>
        </template>
        <div
          v-if="runtime.ready && runtime.resources.length"
          ref="modeChartEl"
          class="chart"
        />
        <Skeleton
          v-else-if="runtime.loading"
          variant="chart"
        />
        <el-empty
          v-else
          :description="runtime.failed ? '未能取得资源档位分布' : '暂无已登记资源'"
          :image-size="60"
        />
      </el-card>

      <el-card
        class="chart-card"
        shadow="never"
      >
        <template #header>
          <span class="chart-title">ORM 对账分布（shadow 证据）</span>
        </template>
        <div
          v-if="runtime.ready && runtime.resources.length"
          ref="parityChartEl"
          class="chart"
        />
        <Skeleton
          v-else-if="runtime.loading"
          variant="chart"
        />
        <el-empty
          v-else
          :description="runtime.failed ? '未能取得 ORM 对账分布' : '暂无对账样本'"
          :image-size="60"
        />
      </el-card>
    </div>
  </section>
</template>

<style lang="scss" scoped>
.security-dashboard {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 0 10px 10px;
}

.dashboard-header {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;

  .title {
    margin: 0;
    font-size: 16px;
    font-weight: bold;
    color: #333;
  }
}

.summary-alert {
  width: 100%;
}

.cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}

.stat-card {
  border-radius: 8px;

  .stat-label {
    font-size: 13px;
    color: #909399;
    margin-bottom: 6px;
  }

  .stat-value {
    font-size: 26px;
    font-weight: bold;
    line-height: 1.2;

    &.alert {
      color: var(--el-color-danger);
    }

    &.normal {
      color: var(--el-color-primary);
    }
  }

  .stat-detail {
    margin-top: 6px;
    font-size: 12px;
    color: #909399;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
}

.charts {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.chart-card {
  border-radius: 8px;

  .chart-title {
    font-size: 14px;
    font-weight: bold;
    color: #333;
  }

  .chart {
    height: 260px;
  }
}

@media (max-width: 900px) {
  .cards {
    grid-template-columns: repeat(2, 1fr);
  }

  .charts {
    grid-template-columns: 1fr;
  }
}
</style>
