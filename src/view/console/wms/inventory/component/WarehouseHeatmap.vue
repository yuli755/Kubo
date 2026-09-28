<script lang="ts" setup>
import type { EChartsType } from '@/utils/echarts'
import {
  Box,
  Coordinate,
  Filter,
  Grid,
  Monitor,
  Search,
  Switch,
} from '@element-plus/icons-vue'
import {
  ElButton,
  ElDrawer,
  ElEmpty,
  ElInput,
  ElMessage,
  ElOption,
  ElProgress,
  ElRadioButton,
  ElRadioGroup,
  ElSelect,
  ElTag,
  ElTooltip,
} from 'element-plus'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { chartTextColor, occupancyColor, occupancyRamp, resolveCssToken } from '@/config/palette'
import { init } from '@/utils/echarts'

export interface ISlotInfo {
  id: string
  code: string
  zone: string
  aisle: number
  level: number
  depth: number
  materialName: string
  materialCode: string
  specification: string
  batchNo: string
  quantity: number
  capacity: number
  occupancy: number
  unit: string
  daysInStock: number
  status: 'normal' | 'warning' | 'idle' | 'locked'
}

defineProps<{
  storageId?: number | null
  storageName?: string
}>()

const router = useRouter()

/** 视角模式：2.5D 立体网格 vs ECharts 热力色阶矩阵 */
const displayMode = ref<'isometric' | 'matrix'>('isometric')
/** 当前选中的库区 */
const activeZone = ref('ZONE_A')
/** 占用率筛选条件 */
const occupancyFilter = ref<'all' | 'high' | 'normal' | 'idle'>('all')
/** 储位物料/编码搜索关键词 */
const searchKeyword = ref('')
/** 货架排数过滤 */
const aisleFilter = ref<number | 'all'>('all')

const isSearching = computed(() => searchKeyword.value.trim().length > 0)

function toggleOccupancyFilter(target: 'high' | 'idle') {
  if (occupancyFilter.value === target) {
    occupancyFilter.value = 'all'
  }
  else {
    occupancyFilter.value = target
  }
}

/** 抽屉展开状态与当前选中的储位 */
const drawerVisible = ref(false)
const selectedSlot = ref<ISlotInfo | null>(null)

/** 库区定义 */
const zones = [
  { key: 'ZONE_A', name: 'A区·标准货架区', description: '轻中型标准托盘与箱装物料' },
  { key: 'ZONE_B', name: 'B区·高位立体库', description: '穿梭车自动存取立体高位货架' },
  { key: 'ZONE_C', name: 'C区·恒温冷链区', description: '温湿度监控精密元件与试剂' },
  { key: 'ZONE_D', name: 'D区·重型原料区', description: '大宗钢卷、线材与重型毛坯' },
]

/** 模拟生成具有代表性的真实仓储货位拓扑数据集 (4 库区 x 6 排 x 4 层 x 3 位 = 72 个/区) */
const allSlots = ref<ISlotInfo[]>(generateWarehouseSlots())

function generateWarehouseSlots(): ISlotInfo[] {
  const result: ISlotInfo[] = []
  const materials = [
    { name: '微型伺服电机 24V', code: 'MTR-24V-01', spec: '24V/100W', unit: '台' },
    { name: '精密行星减速机', code: 'GR-PL-080', spec: '速比 1:10', unit: '套' },
    { name: '高频传感器芯片', code: 'SNS-HF-05', spec: '0-100kHz', unit: '片' },
    { name: '工业光电编码器', code: 'ENC-OPT-1024', spec: '1024脉冲', unit: '个' },
    { name: 'PLC 主控模块', code: 'PLC-CPU-X1', spec: '双网口/32点', unit: '块' },
    { name: '步进电机驱动板', code: 'DRV-ST-4A', spec: 'DC24-48V/4A', unit: '块' },
  ]

  const zoneKeys = ['ZONE_A', 'ZONE_B', 'ZONE_C', 'ZONE_D']

  zoneKeys.forEach((zoneKey, zIdx) => {
    const aislesCount = 6 // 6 排货架
    const levelsCount = 4 // 4 层
    const depthCount = 3 // 每层 3 个储位

    for (let aisle = 1; aisle <= aislesCount; aisle++) {
      for (let level = 1; level <= levelsCount; level++) {
        for (let depth = 1; depth <= depthCount; depth++) {
          const slotId = `${zoneKey}-A${aisle}-L${level}-D${depth}`
          const code = `WH${String(zIdx + 1).padStart(2, '0')}-Z${zoneKey.slice(-1)}-${String(aisle).padStart(2, '0')}-${level}-${depth}`

          // 模拟真实占用率特征分布
          const seed = (zIdx * 100 + aisle * 17 + level * 7 + depth * 3) % 100
          let occupancy = seed
          let status: ISlotInfo['status'] = 'normal'

          if (occupancy > 85) {
            status = 'warning'
          }
          else if (occupancy < 20) {
            status = 'idle'
            occupancy = occupancy < 5 ? 0 : occupancy
          }

          const mat = materials[(aisle + level + depth) % materials.length]
          const capacity = 100
          const quantity = Math.round((capacity * occupancy) / 100)

          result.push({
            id: slotId,
            code,
            zone: zoneKey,
            aisle,
            level,
            depth,
            materialName: occupancy === 0 ? '空储位' : mat.name,
            materialCode: occupancy === 0 ? '-' : mat.code,
            specification: occupancy === 0 ? '-' : mat.spec,
            batchNo: occupancy === 0 ? '-' : `BAT-2026${String(zIdx + 8).padStart(2, '0')}${String(aisle * 4 + level).padStart(2, '0')}`,
            quantity,
            capacity,
            occupancy,
            unit: occupancy === 0 ? '-' : mat.unit,
            daysInStock: occupancy === 0 ? 0 : (seed % 45) + 3,
            status,
          })
        }
      }
    }
  })

  return result
}

/** 当前库区的储位集合 */
const currentZoneSlots = computed(() => {
  return allSlots.value.filter(s => s.zone === activeZone.value)
})

const matchingSlotIds = computed(() => {
  const kw = searchKeyword.value.trim().toLowerCase()
  if (!kw)
    return new Set<string>()
  const matched = new Set<string>()
  currentZoneSlots.value.forEach((s) => {
    if (
      s.code.toLowerCase().includes(kw)
      || s.materialName.toLowerCase().includes(kw)
      || s.materialCode.toLowerCase().includes(kw)
      || s.specification.toLowerCase().includes(kw)
      || s.batchNo.toLowerCase().includes(kw)
    ) {
      matched.add(s.id)
    }
  })
  return matched
})

/** 经过占用率过滤后的储位 */
const filteredSlots = computed(() => {
  return currentZoneSlots.value.filter((s) => {
    if (occupancyFilter.value === 'high')
      return s.occupancy >= 80
    if (occupancyFilter.value === 'idle')
      return s.occupancy <= 20
    if (occupancyFilter.value === 'normal')
      return s.occupancy > 20 && s.occupancy < 80
    return true
  })
})

/** 统计概览指标 */
const heatmapMetrics = computed(() => {
  const slots = currentZoneSlots.value
  const total = slots.length
  if (total === 0)
    return { total: 0, avgOccupancy: 0, highCount: 0, idleCount: 0 }

  const sumOccupancy = slots.reduce((acc, s) => acc + s.occupancy, 0)
  const avgOccupancy = Math.round(sumOccupancy / total)
  const highCount = slots.filter(s => s.occupancy >= 80).length
  const idleCount = slots.filter(s => s.occupancy <= 20).length

  return { total, avgOccupancy, highCount, idleCount }
})

/** 按排（Aisle）聚合的货架列表（便于渲染 2.5D 货架网格） */
const aislesGrouped = computed(() => {
  const groups: Record<number, ISlotInfo[]> = {}
  currentZoneSlots.value.forEach((slot) => {
    if (!groups[slot.aisle]) {
      groups[slot.aisle] = []
    }
    groups[slot.aisle].push(slot)
  })
  return Object.keys(groups)
    .map(Number)
    .sort((a, b) => a - b)
    .map(aisle => ({
      aisle,
      name: `第 ${aisle} 排货架`,
      slots: groups[aisle].sort((a, b) => {
        // 层高从高到低渲染（最上层在视觉顶部），同层按进深排序
        if (b.level !== a.level)
          return b.level - a.level
        return a.depth - b.depth
      }),
    }))
})

/** 经过排数筛选的货架聚合 */
const displayedAisles = computed(() => {
  if (aisleFilter.value === 'all') {
    return aislesGrouped.value
  }
  return aislesGrouped.value.filter(g => g.aisle === aisleFilter.value)
})

/** 获取储位色块样式 */
function getSlotColorClass(slot: ISlotInfo): string {
  if (slot.occupancy === 0)
    return 'is-empty'
  if (slot.occupancy <= 20)
    return 'is-idle'
  if (slot.occupancy <= 60)
    return 'is-low'
  if (slot.occupancy <= 80)
    return 'is-normal'
  return 'is-danger'
}

/** 点击储位卡片，打开抽屉 */
function onSelectSlot(slot: ISlotInfo) {
  selectedSlot.value = slot
  drawerVisible.value = true
}

/** 快捷发起移库 */
function handleStartMove() {
  drawerVisible.value = false
  ElMessage.success(`已复制源储位 [${selectedSlot.value?.code}]，正在跳转调拨移库单...`)
  router.push('/console/wms/move/list')
}

/** 快捷发起盘点 */
function handleInventoryCheck() {
  ElMessage.success(`储位 [${selectedSlot.value?.code}] 盘点打标完成，账实一致。`)
  drawerVisible.value = false
}

// ─────────────────────────────────────────────────────────────
// ECharts 热力色阶矩阵渲染
// ─────────────────────────────────────────────────────────────
const chartContainerRef = ref<HTMLDivElement>()
let chartInstance: EChartsType | null = null

function renderEChartsHeatmap() {
  if (!chartContainerRef.value)
    return
  if (!chartInstance) {
    chartInstance = init(chartContainerRef.value)
    chartInstance.on('click', (params: any) => {
      if (params.data && params.data[3]) {
        onSelectSlot(params.data[3] as ISlotInfo)
      }
    })
  }

  const aisles = ['排 01', '排 02', '排 03', '排 04', '排 05', '排 06']
  const levels = ['层 01 (底层)', '层 02 (二层)', '层 03 (三层)', '层 04 (顶层)']

  // 数据格式：[aisleIdx, levelIdx, occupancy, slotInfo]
  const data: Array<[number, number, number, ISlotInfo]> = []

  currentZoneSlots.value.forEach((slot) => {
    // 聚合每排每层的平均占用率展示在热力矩阵中
    const x = slot.aisle - 1
    const y = slot.level - 1
    data.push([x, y, slot.occupancy, slot])
  })

  chartInstance.setOption({
    tooltip: {
      position: 'top',
      formatter: (p: any) => {
        const slot = p.data[3] as ISlotInfo
        return `
          <div style="font-size:12px;line-height:1.6">
            <strong>${slot.code}</strong><br/>
            物料：${slot.materialName}<br/>
            现存：${slot.quantity} ${slot.unit} / 额定 ${slot.capacity}<br/>
            占用率：<strong>${slot.occupancy}%</strong>
          </div>
        `
      },
    },
    grid: {
      top: 30,
      bottom: 60,
      left: 100,
      right: 40,
    },
    xAxis: {
      type: 'category',
      data: aisles,
      splitArea: { show: true },
      axisLabel: { color: chartTextColor(), fontWeight: 'bold' },
    },
    yAxis: {
      type: 'category',
      data: levels,
      splitArea: { show: true },
      axisLabel: { color: chartTextColor(), fontWeight: 'bold' },
    },
    visualMap: {
      min: 0,
      max: 100,
      calculable: true,
      orient: 'horizontal',
      left: 'center',
      bottom: 5,
      inRange: {
        // 色带取自 palette 的 OCCUPANCY_LEVELS 同一张表（排除 empty 档，理由见 occupancyRamp 注释）。
        // 原来这里写死 4 个十六进制，与图例的 5 个 .dot-* 类、进度条的 3 档三元各写一份，
        // 同一个储位能在同屏显示两种颜色；现在三处同源。
        color: occupancyRamp(),
      },
      text: ['满载 (100%)', '空闲 (0%)'],
      textStyle: { color: chartTextColor(), fontSize: 11 },
    },
    series: [
      {
        name: '库位热力',
        type: 'heatmap',
        data,
        label: {
          show: true,
          formatter: (p: any) => `${p.data[2]}%`,
          // 格子上的百分比文字：饱和色块上白字在亮/暗两种主题下都成立，故取 --el-color-white
          color: resolveCssToken('--el-color-white', '#ffffff'),
          fontWeight: 600,
          fontSize: 11,
        },
        itemStyle: {
          // 格子分隔线取**背景色**而不是写死白色：亮色下 --el-bg-color 就是 #fff，
          // 与改动前完全一致（零视觉变化）；暗色下它会变成深色，格子之间不再被白边割裂。
          borderColor: resolveCssToken('--el-bg-color', '#ffffff'),
          borderWidth: 2,
          borderRadius: 4,
        },
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowColor: 'rgba(0, 0, 0, 0.4)',
          },
        },
      },
    ],
  })
}

function handleResize() {
  chartInstance?.resize()
}

watch([displayMode, activeZone, occupancyFilter], () => {
  if (displayMode.value === 'matrix') {
    nextTick(() => {
      renderEChartsHeatmap()
      handleResize()
    })
  }
})

onMounted(() => {
  window.addEventListener('resize', handleResize)
  if (displayMode.value === 'matrix') {
    nextTick(() => renderEChartsHeatmap())
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chartInstance?.dispose()
  chartInstance = null
})
</script>

<template>
  <div class="warehouse-heatmap">
    <!-- 顶栏状态与视图切换区 -->
    <div class="heatmap-header">
      <div class="header-left">
        <span class="storage-title">
          <Coordinate class="icon" />
          {{ storageName }} · 库位三维态势与热力拓扑
        </span>
        <ElTag
          size="small"
          type="success"
          effect="light"
        >
          实时监控中
        </ElTag>
      </div>

      <div class="header-right">
        <!-- 视角切换 -->
        <ElRadioGroup
          v-model="displayMode"
          size="small"
        >
          <ElRadioButton value="isometric">
            <template #default>
              <Grid class="btn-icon" /> 2.5D 货架立面
            </template>
          </ElRadioButton>
          <ElRadioButton value="matrix">
            <template #default>
              <Monitor class="btn-icon" /> ECharts 热力色阶
            </template>
          </ElRadioButton>
        </ElRadioGroup>
      </div>
    </div>

    <!-- 库区选择与指标看板 -->
    <div class="zone-bar">
      <div class="zone-tabs">
        <button
          v-for="z in zones"
          :key="z.key"
          class="zone-tab-btn"
          :class="{ 'is-active': activeZone === z.key }"
          @click="activeZone = z.key"
        >
          <span class="tab-title">{{ z.name }}</span>
          <span class="tab-desc">{{ z.description }}</span>
        </button>
      </div>

      <!-- 实时指标胶囊 -->
      <div class="metrics-row">
        <div
          class="metric-card is-clickable"
          @click="occupancyFilter = 'all'"
        >
          <span class="m-label">总监控储位</span>
          <span class="m-value font-mono">{{ heatmapMetrics.total }}</span>
        </div>
        <div
          class="metric-card is-clickable"
          @click="occupancyFilter = 'all'"
        >
          <span class="m-label">库区平均利用率</span>
          <span class="m-value font-mono text-primary">{{ heatmapMetrics.avgOccupancy }}%</span>
        </div>
        <div
          class="metric-card is-danger is-clickable"
          :class="{ 'is-active': occupancyFilter === 'high' }"
          @click="toggleOccupancyFilter('high')"
        >
          <span class="m-label">满载/高危储位 (&gt;80%)</span>
          <span class="m-value font-mono">{{ heatmapMetrics.highCount }}</span>
        </div>
        <div
          class="metric-card is-idle is-clickable"
          :class="{ 'is-active': occupancyFilter === 'idle' }"
          @click="toggleOccupancyFilter('idle')"
        >
          <span class="m-label">空闲/低位储位 (&lt;20%)</span>
          <span class="m-value font-mono">{{ heatmapMetrics.idleCount }}</span>
        </div>
      </div>
    </div>

    <!-- 过滤工具栏与状态图例 -->
    <div class="toolbar-row">
      <div class="toolbar-left-controls">
        <div class="filter-group">
          <span class="filter-label"><Filter class="icon" /> 状态筛选：</span>
          <ElRadioGroup
            v-model="occupancyFilter"
            size="small"
          >
            <ElRadioButton value="all">
              全量储位
            </ElRadioButton>
            <ElRadioButton value="high">
              饱和高载 (&ge;80%)
            </ElRadioButton>
            <ElRadioButton value="normal">
              正常周转 (20~80%)
            </ElRadioButton>
            <ElRadioButton value="idle">
              空闲闲置 (&le;20%)
            </ElRadioButton>
          </ElRadioGroup>
        </div>

        <!-- 货架排数过滤 -->
        <div class="aisle-select-group">
          <ElSelect
            v-model="aisleFilter"
            size="small"
            style="width: 120px"
          >
            <ElOption
              value="all"
              label="全部货架排"
            />
            <ElOption
              v-for="i in 6"
              :key="i"
              :value="i"
              :label="`第 ${i} 排货架`"
            />
          </ElSelect>
        </div>

        <!-- 物料 / 条码实时定位搜索 -->
        <div class="search-group">
          <ElInput
            v-model="searchKeyword"
            placeholder="搜索储位 / 物料 / 批次..."
            size="small"
            clearable
            :prefix-icon="Search"
            style="width: 220px"
          />
          <ElTag
            v-if="isSearching"
            size="small"
            type="warning"
            effect="light"
          >
            匹配 {{ matchingSlotIds.size }} 个货位
          </ElTag>
        </div>
      </div>

      <div class="legend-group">
        <span class="legend-item"><span class="color-dot dot-empty" /> 0% 空置</span>
        <span class="legend-item"><span class="color-dot dot-idle" /> 1~20% 闲置</span>
        <span class="legend-item"><span class="color-dot dot-low" /> 21~60% 适中</span>
        <span class="legend-item"><span class="color-dot dot-normal" /> 61~80% 繁忙</span>
        <span class="legend-item"><span class="color-dot dot-danger" /> &gt;80% 满载</span>
      </div>
    </div>

    <!-- 视图呈现区 -->
    <div class="view-viewport">
      <!-- 模式一：2.5D 货架立面网格 -->
      <div
        v-if="displayMode === 'isometric'"
        class="isometric-grid-wrap"
      >
        <div class="racks-container">
          <div
            v-for="rack in displayedAisles"
            :key="rack.aisle"
            class="rack-card"
          >
            <div class="rack-card__header">
              <span class="rack-name">{{ rack.name }}</span>
              <ElTag
                size="small"
                type="info"
                effect="plain"
              >
                4 层 x 3 位
              </ElTag>
            </div>

            <!-- 货架立面货位单元格矩阵 -->
            <div class="shelf-matrix">
              <div
                v-for="slot in rack.slots"
                :key="slot.id"
                class="slot-cell"
                :class="[
                  getSlotColorClass(slot),
                  {
                    'is-filtered-out': !filteredSlots.some(s => s.id === slot.id) || (isSearching && !matchingSlotIds.has(slot.id)),
                    'is-matched': isSearching && matchingSlotIds.has(slot.id),
                  },
                ]"
                @click="onSelectSlot(slot)"
              >
                <ElTooltip
                  effect="dark"
                  placement="top"
                  :content="`${slot.code} | ${slot.materialName} | 占用率 ${slot.occupancy}%`"
                >
                  <div class="slot-inner">
                    <span class="slot-code">L{{ slot.level }}-{{ slot.depth }}</span>
                    <span class="slot-percent font-mono">{{ slot.occupancy }}%</span>
                  </div>
                </ElTooltip>
              </div>
            </div>

            <div class="rack-card__base">
              <span class="base-rail" />
            </div>
          </div>
        </div>
      </div>

      <!-- 模式二：ECharts 热力矩阵图 -->
      <div
        v-else
        class="matrix-chart-wrap"
      >
        <div
          ref="chartContainerRef"
          class="echarts-dom"
        />
      </div>
    </div>

    <!-- 储位详情反查与快捷操作抽屉 -->
    <ElDrawer
      v-model="drawerVisible"
      title="货架储位立体明细与物料反查"
      size="420px"
      append-to-body
      destroy-on-close
    >
      <div
        v-if="selectedSlot"
        class="slot-drawer-content"
      >
        <!-- 储位状态徽章与大标题 -->
        <div class="drawer-hero">
          <div class="hero-code font-mono">
            {{ selectedSlot.code }}
          </div>
          <div class="hero-sub">
            <span>{{ selectedSlot.zone }}</span>
            <span class="divider">/</span>
            <span>第 {{ selectedSlot.aisle }} 排</span>
            <span class="divider">/</span>
            <span>第 {{ selectedSlot.level }} 层</span>
            <span class="divider">/</span>
            <span>位 {{ selectedSlot.depth }}</span>
          </div>

          <div class="hero-tags">
            <ElTag
              v-if="selectedSlot.occupancy === 0"
              type="info"
            >
              空置储位
            </ElTag>
            <ElTag
              v-else-if="selectedSlot.occupancy >= 80"
              type="danger"
            >
              🔥 饱和满载
            </ElTag>
            <ElTag
              v-else-if="selectedSlot.occupancy <= 20"
              type="success"
            >
              🧊 低负荷
            </ElTag>
            <ElTag
              v-else
              type="primary"
            >
              常态在库
            </ElTag>
            <ElTag
              effect="plain"
              type="info"
            >
              额定承重: 1,500 kg
            </ElTag>
          </div>
        </div>

        <!-- 空间占用率进度条 -->
        <div class="section-card">
          <div class="card-title">
            储位空间占用率
          </div>
          <div class="occupancy-meter">
            <ElProgress
              :percentage="selectedSlot.occupancy"
              :color="occupancyColor(selectedSlot.occupancy)"
              :stroke-width="16"
              striped
              striped-flow
            />
            <div class="meter-meta">
              <span>已存容积：<strong>{{ selectedSlot.quantity }} {{ selectedSlot.unit }}</strong></span>
              <span>额定总容：<strong>{{ selectedSlot.capacity }} {{ selectedSlot.unit }}</strong></span>
            </div>
          </div>
        </div>

        <!-- 存放物料明细 -->
        <div class="section-card">
          <div class="card-title">
            当前存放物料档案
          </div>
          <div
            v-if="selectedSlot.occupancy > 0"
            class="info-kv-grid"
          >
            <div class="kv-row">
              <span class="kv-key">物料名称：</span>
              <span class="kv-val font-bold">{{ selectedSlot.materialName }}</span>
            </div>
            <div class="kv-row">
              <span class="kv-key">物料编码：</span>
              <span class="kv-val font-mono">{{ selectedSlot.materialCode }}</span>
            </div>
            <div class="kv-row">
              <span class="kv-key">规格型号：</span>
              <span class="kv-val">{{ selectedSlot.specification }}</span>
            </div>
            <div class="kv-row">
              <span class="kv-key">生产批次：</span>
              <span class="kv-val font-mono text-primary">{{ selectedSlot.batchNo }}</span>
            </div>
            <div class="kv-row">
              <span class="kv-key">当前在库：</span>
              <span class="kv-val font-mono">{{ selectedSlot.quantity }} {{ selectedSlot.unit }}</span>
            </div>
            <div class="kv-row">
              <span class="kv-key">在库库龄：</span>
              <span class="kv-val font-mono">{{ selectedSlot.daysInStock }} 天</span>
            </div>
          </div>
          <ElEmpty
            v-else
            description="该储位当前无在库物料"
            :image-size="60"
          />
        </div>

        <!-- 快捷操作栏 -->
        <div class="drawer-actions">
          <ElButton
            type="primary"
            :icon="Switch"
            :disabled="selectedSlot.occupancy === 0"
            @click="handleStartMove"
          >
            发起调拨 / 移库
          </ElButton>
          <ElButton
            :icon="Box"
            @click="handleInventoryCheck"
          >
            储位核对盘点
          </ElButton>
        </div>
      </div>
    </ElDrawer>
  </div>
</template>

<style lang="scss" scoped>
.warehouse-heatmap {
  display: flex;
  flex-direction: column;
  gap: 12px;
  background: var(--el-bg-color);
}

.heatmap-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
  border: 1px solid var(--el-border-color-lighter);

  .header-left {
    display: flex;
    align-items: center;
    gap: 10px;

    .storage-title {
      font-size: 14px;
      font-weight: 700;
      color: var(--el-text-color-primary);
      display: flex;
      align-items: center;
      gap: 6px;

      .icon {
        width: 16px;
        height: 16px;
        color: var(--el-color-primary);
      }
    }
  }

  .btn-icon {
    width: 14px;
    height: 14px;
    vertical-align: -2px;
    margin-right: 4px;
  }
}

.zone-bar {
  display: flex;
  flex-direction: column;
  gap: 10px;

  .zone-tabs {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
    gap: 8px;

    .zone-tab-btn {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      gap: 3px;
      padding: 8px 12px;
      background: var(--el-bg-color);
      border: 1px solid var(--el-border-color);
      border-radius: 6px;
      cursor: pointer;
      text-align: left;
      transition: all 0.2s ease;

      .tab-title {
        font-size: 13px;
        font-weight: 600;
        color: var(--el-text-color-primary);
      }

      .tab-desc {
        font-size: 11px;
        color: var(--el-text-color-secondary);
      }

      &:hover {
        border-color: var(--el-color-primary-light-3);
        background: var(--el-color-primary-light-9);
      }

      &.is-active {
        border-color: var(--el-color-primary);
        background: var(--el-color-primary-light-9);
        box-shadow: 0 0 0 1px var(--el-color-primary);

        .tab-title {
          color: var(--el-color-primary);
        }
      }
    }
  }

  .metrics-row {
    display: flex;
    gap: 12px;
    flex-wrap: wrap;

    .metric-card {
      flex: 1;
      min-width: 140px;
      padding: 8px 14px;
      background: var(--el-fill-color-light);
      border: 1px solid var(--el-border-color-lighter);
      border-radius: 6px;
      display: flex;
      flex-direction: column;
      gap: 2px;

      .m-label {
        font-size: 11px;
        color: var(--el-text-color-secondary);
      }

      .m-value {
        font-size: 18px;
        font-weight: 700;
        color: var(--el-text-color-primary);
      }

      &.is-danger .m-value {
        color: var(--kubo-occupancy-danger);
      }

      &.is-idle .m-value {
        color: var(--kubo-occupancy-idle);
      }

      &.is-clickable {
        cursor: pointer;
        transition: all 0.2s ease;

        &:hover {
          border-color: var(--el-color-primary);
          background: var(--el-fill-color);
          transform: translateY(-1px);
        }

        &.is-active {
          border-color: var(--el-color-primary);
          box-shadow: 0 0 0 1px var(--el-color-primary);
          background: var(--el-color-primary-light-9);
        }
      }
    }
  }
}

.toolbar-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: var(--el-fill-color-extra-light);
  border-radius: 6px;
  flex-wrap: wrap;
  gap: 12px;

  .toolbar-left-controls {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;

    .filter-group {
      display: flex;
      align-items: center;
      gap: 6px;

      .filter-label {
        font-size: 12px;
        color: var(--el-text-color-secondary);
        display: flex;
        align-items: center;
        gap: 4px;

        .icon {
          width: 14px;
          height: 14px;
        }
      }
    }

    .aisle-select-group {
      display: flex;
      align-items: center;
    }

    .search-group {
      display: flex;
      align-items: center;
      gap: 8px;
    }
  }

  .legend-group {
    display: flex;
    align-items: center;
    gap: 12px;

    .legend-item {
      display: flex;
      align-items: center;
      gap: 4px;
      font-size: 11px;
      color: var(--el-text-color-secondary);

      .color-dot {
        width: 10px;
        height: 10px;
        border-radius: 2px;

        &.dot-empty { background: var(--kubo-occupancy-empty); border: 1px solid var(--kubo-occupancy-empty-border); }
        &.dot-idle { background: var(--kubo-occupancy-idle); }
        &.dot-low { background: var(--kubo-occupancy-low); }
        &.dot-normal { background: var(--kubo-occupancy-normal); }
        &.dot-danger { background: var(--kubo-occupancy-danger); }
      }
    }
  }
}

.view-viewport {
  min-height: 420px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 16px;
  background: var(--el-fill-color-blank);
}

// 2.5D 立体网格样式
.isometric-grid-wrap {
  overflow-x: auto;

  .racks-container {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
    gap: 16px;
    align-items: start;
  }

  .rack-card {
    background: var(--el-fill-color-light);
    border: 1px solid var(--el-border-color);
    border-radius: 6px;
    padding: 10px;
    display: flex;
    flex-direction: column;
    gap: 8px;
    box-shadow: var(--kubo-shadow-sm);

    &__header {
      display: flex;
      justify-content: space-between;
      align-items: center;

      .rack-name {
        font-size: 12px;
        font-weight: 700;
        color: var(--el-text-color-primary);
      }
    }

    .shelf-matrix {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 6px;
    }

    .slot-cell {
      border-radius: 4px;
      padding: 6px 4px;
      text-align: center;
      cursor: pointer;
      transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
      border: 1px solid transparent;
      user-select: none;

      .slot-inner {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 2px;

        .slot-code {
          font-size: 10px;
          font-weight: 600;
          opacity: 0.85;
        }

        .slot-percent {
          font-size: 11px;
          font-weight: 700;
        }
      }

      &:hover {
        transform: translateY(-2px) scale(1.05);
        box-shadow: var(--kubo-shadow-lg);
        z-index: 2;
      }

      /* 五档占用徽章。底色 / 文字 / 描边全部取 EP 的 light-9 / dark-2 / light-7 三级色阶：
       * 一是暗色模式自动适配（原来写死 Tailwind 的 50/600/200 三色一组，暗色下对比度不足），
       * 二是色相与 --kubo-occupancy-* 对齐（见 tokens.scss），
       *    于是热力格、图例点、进度条、徽章四处同源，不再各写一份。 */
      &.is-empty {
        background: var(--el-color-info-light-9);
        color: var(--el-color-info-dark-2);
        border-color: var(--el-color-info-light-7);
      }

      &.is-idle {
        background: var(--el-color-success-light-9);
        color: var(--el-color-success-dark-2);
        border-color: var(--el-color-success-light-7);
      }

      &.is-low {
        background: var(--el-color-primary-light-9);
        color: var(--el-color-primary-dark-2);
        border-color: var(--el-color-primary-light-7);
      }

      &.is-normal {
        background: var(--el-color-warning-light-9);
        color: var(--el-color-warning-dark-2);
        border-color: var(--el-color-warning-light-7);
      }

      &.is-danger {
        background: var(--el-color-danger-light-9);
        color: var(--el-color-danger-dark-2);
        border-color: var(--el-color-danger-light-7);
        font-weight: 700;
      }

      &.is-matched {
        border-color: var(--el-color-primary) !important;
        transform: translateY(-2px) scale(1.08);
        z-index: 5;
        animation: pulse-match 1.8s infinite;
      }

      &.is-filtered-out {
        opacity: 0.15;
        filter: grayscale(80%);
      }
    }

    &__base {
      display: flex;
      justify-content: center;
      margin-top: 2px;

      .base-rail {
        width: 80%;
        height: 3px;
        background: var(--el-border-color-darker);
        border-radius: 2px;
      }
    }
  }
}

// ECharts 矩阵图样式
.matrix-chart-wrap {
  width: 100%;
  height: 420px;

  .echarts-dom {
    width: 100%;
    height: 100%;
  }
}

// 抽屉内部样式
.slot-drawer-content {
  display: flex;
  flex-direction: column;
  gap: 18px;

  .drawer-hero {
    background: var(--el-fill-color-light);
    border: 1px solid var(--el-border-color-lighter);
    padding: 14px;
    border-radius: 6px;

    .hero-code {
      font-size: 18px;
      font-weight: 800;
      color: var(--el-text-color-primary);
    }

    .hero-sub {
      font-size: 12px;
      color: var(--el-text-color-secondary);
      margin: 4px 0 10px 0;

      .divider {
        margin: 0 4px;
        opacity: 0.5;
      }
    }

    .hero-tags {
      display: flex;
      gap: 6px;
      flex-wrap: wrap;
    }
  }

  .section-card {
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 6px;
    padding: 12px;

    .card-title {
      font-size: 12px;
      font-weight: 700;
      color: var(--el-text-color-secondary);
      margin-bottom: 10px;
    }

    .occupancy-meter {
      display: flex;
      flex-direction: column;
      gap: 8px;

      .meter-meta {
        display: flex;
        justify-content: space-between;
        font-size: 12px;
        color: var(--el-text-color-regular);
      }
    }

    .info-kv-grid {
      display: flex;
      flex-direction: column;
      gap: 8px;

      .kv-row {
        display: flex;
        font-size: 13px;

        .kv-key {
          width: 80px;
          color: var(--el-text-color-secondary);
          flex-shrink: 0;
        }

        .kv-val {
          color: var(--el-text-color-primary);
          word-break: break-all;
        }
      }
    }
  }

  .drawer-actions {
    display: flex;
    gap: 12px;
    margin-top: 10px;

    :deep(.el-button) {
      flex: 1;
    }
  }
}

@keyframes pulse-match {
  /* 光晕色也走 token。原来两处都把颜色写死：主色光晕是 `rgba(64, 158, 255, 0.4)`
   * （即 #409eff 加 40% 透明度），中档光晕是 `#f59e0b` + `rgba(245, 158, 11, 0.7)`。
   * 前者与紧邻的 `var(--el-color-primary)` 自相矛盾：一旦 tokens.scss 改主色，
   * 圆环跟着变、光晕却停在旧蓝色上。改用同色相的 light-5 / light-3 得到层次。
   * 取舍：token 是不透明色而非 alpha 色，光晕会略微实一点；
   * 换来的是「改主色只改一处」这条约束真正成立。 */
  0% {
    box-shadow: 0 0 0 2px var(--el-color-primary), 0 0 6px var(--el-color-primary-light-5);
  }
  50% {
    box-shadow: 0 0 0 3px var(--kubo-occupancy-normal), 0 0 14px var(--el-color-warning-light-3);
  }
  100% {
    box-shadow: 0 0 0 2px var(--el-color-primary), 0 0 6px var(--el-color-primary-light-5);
  }
}
</style>
