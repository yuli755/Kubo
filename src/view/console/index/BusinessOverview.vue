<script lang="ts" setup>
import { RefreshRight } from '@element-plus/icons-vue'
import { useBusinessOverview } from '@/composables/useBusinessOverview'

/**
 * # 业务概况（P2 · 数据权限感知看板）
 *
 * 「我」在当前租户与行级权限下、各资源有多少行。数据来自 `dashboard/overview`（后端对每个资源
 * 走 `getMapperCount` 权限拦截链再聚合），本组件只回显 count、不重算任何聚合判定。
 *
 * 与「安全态势看板」（仅数据权限管理身份可见）不同，业务概况对<b>所有登录用户</b>可见 ——
 * 它回答的是「我能看到多少」，不含跨租户元数据。
 */

const overview = useBusinessOverview()
</script>

<template>
  <section class="business-overview">
    <div class="overview-header">
      <h3 class="title">
        业务概况
      </h3>
      <el-button
        :loading="overview.loading"
        :icon="RefreshRight"
        circle
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
    <div
      v-else
      class="grid"
    >
      <el-card
        v-for="item in overview.items"
        :key="item.resourceKey"
        class="item"
        shadow="never"
      >
        <div class="label">
          {{ item.label }}
        </div>
        <div class="count">
          {{ item.count }}
        </div>
      </el-card>
    </div>
  </section>
</template>

<style lang="scss" scoped>
.business-overview {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 10px;
}

.overview-header {
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

.overview-alert {
  width: 100%;
}

.grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 10px;
}

.item {
  border-radius: 8px;

  .label {
    font-size: 13px;
    color: #909399;
    margin-bottom: 6px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .count {
    font-size: 24px;
    font-weight: bold;
    color: var(--el-color-primary);
    line-height: 1.2;
  }
}

@media (max-width: 1200px) {
  .grid {
    grid-template-columns: repeat(4, 1fr);
  }
}

@media (max-width: 700px) {
  .grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
