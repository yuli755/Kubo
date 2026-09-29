<script lang="ts" setup>
import type { Component } from 'vue'
import {
  Box,
  Goods,
  RefreshRight,
  Right,
  Sell,
  ShoppingCart,
  User,
  Van,
} from '@element-plus/icons-vue'
import { markRaw } from 'vue'
import Skeleton from '@/component/Skeleton.vue'
import { useBusinessOverview } from '@/composables/useBusinessOverview'
import { accentOf } from '@/config/palette'
import { getRouter } from '@/router/appRouter'

/**
 * # 业务概况（P2 · 数据权限感知看板）
 *
 * 「我」在当前租户与行级权限下、各资源有多少行。数据来自 `dashboard/overview`（后端对每个资源
 * 走 `getMapperCount` 权限拦截链再聚合），本组件只回显 count、不重算任何聚合判定。
 */
const overview = useBusinessOverview()

interface ResourceVisual {
  icon: Component
  color: string
  bg: string
  path: string
}

/**
 * 资源码 → 图标与落地路由。
 *
 * **颜色不在这里定义**：强调色统一由 `@/config/palette` 的 `MODULE_ACCENTS` 按 path 派发，
 * 首页卡片（`console/index/index.vue`）查的是同一张表。
 * 收拢前两处各写一份，7 个重叠模块的配色**全部不一致**（同一个「采购」在首页是蓝、
 * 在这里是靛），颜色承担的模块身份识别因此失效；详见 palette.ts 头部对照表。
 */
const RESOURCE_CONFIG: Record<string, { icon: Component, path: string }> = {
  'wms.input': { icon: markRaw(Van), path: '/console/wms/input/list' },
  'wms.output': { icon: markRaw(Box), path: '/console/wms/output/list' },
  'wms.inventory': { icon: markRaw(Goods), path: '/console/wms/inventory/list' },
  'channel.purchase': { icon: markRaw(ShoppingCart), path: '/console/channel/purchase/list' },
  'channel.sale': { icon: markRaw(Sell), path: '/console/channel/sale/list' },
  'asset.material': { icon: markRaw(Goods), path: '/console/asset/material/list' },
  'personnel.user': { icon: markRaw(User), path: '/console/personnel/user/list' },
}

/** 未注册资源的兜底；`/console/index` 不在 `MODULE_ACCENTS` 里，故 accentOf 返回中性灰 */
const DEFAULT_RESOURCE = { icon: markRaw(Box), path: '/console/index' }

function getVisual(key: string): ResourceVisual {
  const entry = RESOURCE_CONFIG[key] ?? DEFAULT_RESOURCE
  return { ...entry, ...accentOf(entry.path) }
}

function goResource(key: string) {
  const visual = getVisual(key)
  if (visual.path && visual.path !== '/console/index') {
    getRouter().push(visual.path)
  }
}
</script>

<template>
  <section class="business-overview">
    <div class="overview-header">
      <div class="header-left">
        <h3 class="title">
          业务数据全景
        </h3>
        <span class="sub-tip">当前租户与数据范围内的实体量级 · 点击直接进入业务列表</span>
      </div>
      <el-button
        :loading="overview.loading"
        :icon="RefreshRight"
        circle
        size="small"
        title="刷新统计数据"
        @click="overview.refresh"
      />
    </div>

    <el-alert
      v-if="overview.failed"
      class="overview-alert"
      type="warning"
      :closable="false"
      show-icon
      title="未能取得业务概况"
      description="取不到结果时不会显示为「全部为 0」；请检查 /dashboard/overview 是否可用。"
    />
    <Skeleton
      v-else-if="overview.loading && !overview.ready"
      variant="card"
      :count="7"
    />
    <div
      v-else
      class="grid"
    >
      <div
        v-for="item in overview.items"
        :key="item.resourceKey"
        class="metric-card"
        :style="{
          '--accent-color': getVisual(item.resourceKey).color,
          '--accent-bg': getVisual(item.resourceKey).bg,
        }"
        @click="goResource(item.resourceKey)"
      >
        <div class="card-top">
          <div class="icon-wrap">
            <el-icon :size="18">
              <component :is="getVisual(item.resourceKey).icon" />
            </el-icon>
          </div>
          <span class="label">{{ item.label }}</span>
        </div>
        <div class="card-bottom">
          <span class="count">{{ Number(item.count || 0).toLocaleString() }}</span>
          <span class="jump-hint">
            <span>穿透</span>
            <el-icon :size="12"><Right /></el-icon>
          </span>
        </div>
      </div>
    </div>
  </section>
</template>

<style lang="scss" scoped>
.business-overview {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.overview-header {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;

  .header-left {
    display: flex;
    align-items: baseline;
    gap: 12px;
  }

  .title {
    margin: 0;
    font-size: 16px;
    font-weight: 700;
    color: var(--el-text-color-primary);
  }

  .sub-tip {
    font-size: 13px;
    color: var(--el-text-color-secondary);
  }
}

.overview-alert {
  width: 100%;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 14px;
}

.metric-card {
  position: relative;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  padding: 16px 18px;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    left: 0;
    top: 0;
    bottom: 0;
    width: 3px;
    background: var(--accent-color);
    opacity: 0.85;
    transition: width 0.2s ease;
  }

  &:hover {
    transform: translateY(-2px);
    border-color: var(--accent-color);
    box-shadow: var(--kubo-shadow-base);

    &::before {
      width: 5px;
    }

    .jump-hint {
      color: var(--accent-color);
      transform: translateX(2px);
    }
  }

  .card-top {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 12px;

    .icon-wrap {
      width: 34px;
      height: 34px;
      border-radius: 8px;
      background: var(--accent-bg);
      color: var(--accent-color);
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }

    .label {
      font-size: 13px;
      font-weight: 500;
      color: var(--el-text-color-regular);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }

  .card-bottom {
    display: flex;
    align-items: baseline;
    justify-content: space-between;

    .count {
      font-size: 26px;
      font-weight: 700;
      color: var(--el-text-color-primary);
      line-height: 1;
      font-family: var(--kubo-font-mono);
      font-variant-numeric: tabular-nums;
    }

    .jump-hint {
      display: inline-flex;
      align-items: center;
      gap: 3px;
      font-size: 12px;
      color: var(--el-text-color-secondary);
      transition: all 0.2s ease;
    }
  }
}

@media (max-width: 768px) {
  .overview-header .sub-tip {
    display: none;
  }
}
</style>
