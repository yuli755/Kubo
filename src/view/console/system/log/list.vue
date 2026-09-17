<script lang="ts" setup>
import type { LogEntity } from '@/model/system/log/LogEntity'
import { ElTag } from 'element-plus'
import { computed, ref } from 'vue'
import KPanel from '@/component/framework/KPanel.vue'
import KTable from '@/component/framework/KTable.vue'
import { Permissions } from '@/config/permissions'
import { WebColor } from '@/framework'
import { useTable } from '@/hook/kubo/useTable'
import { LogService } from '@/hook/system/useLog'
import LogDetail from './detail.vue'

/**
 * # 操作日志（F4 批 6 · 新底座）
 *
 * 纯只读列表：`hide-add` / `hide-edit` / `hide-delete` / `hide-enable` 四个开关都要显式关，
 * 因为 `v-permission` 在权限码未传时**不拦截** —— 关掉按钮得靠开关，不是靠"不传权限码"。
 *
 * ## 查看走声明式，不再命令式开弹窗
 * 旧页面 `useTanStackTable(..., { detailView: Detail })` 由 hook 决定开哪个弹窗。
 * 新底座把这件事交回页面：`<LogDetail v-model="detailVisible" :id="detailId" />` ——
 * 弹窗的显隐与数据来源在页面上看得见，也不需要应用上下文（`DialogUtil.show` 那条链路
 * 在批 5 踩过"命令式弹窗没接 appContext 就静默不开"的坑）。
 *
 * ## 耗时那一列是**算出来的**
 * `pendingTime` 由后端给，颜色按耗时档位（<500ms 绿 / <2s 黄 / <5s 红 / 其余灰）——
 * 与旧页面同一套阈值。
 */
const table = useTable(LogService, { queryKey: ['system:log'] })

const detailVisible = ref(false)
const detailId = ref<number | null>(null)

function openDetail(row: LogEntity) {
  detailId.value = row.id
  detailVisible.value = true
}

const actions = computed(() => [{
  label: '查看',
  permission: Permissions.System.Log.getDetail,
  handler: (row: LogEntity) => openDetail(row),
}])

function getColor(log: LogEntity) {
  const diff = (log.updateTime ?? 0) - (log.createTime ?? 0)
  if (diff < 500) {
    return WebColor.SUCCESS
  }
  if (diff < 2000) {
    return WebColor.WARNING
  }
  if (diff < 5000) {
    return WebColor.DANGER
  }
  return WebColor.NORMAL
}
</script>

<template>
  <div class="k-page">
    <KPanel
      title="操作日志"
      description="接口调用的审计流水：动作、耗时、请求体与响应都在详情里"
    >
      <KTable
        :table="table"
        :actions="actions"
        hide-add
        hide-delete
        hide-edit
        hide-enable
      >
        <template #cell-pendingTime="{ row }">
          <ElTag
            :color="getColor(row as LogEntity)"
            disable-transitions
            effect="dark"
          >
            {{ ((row as LogEntity).updateTime ?? 0) - ((row as LogEntity).createTime ?? 0) }}ms
          </ElTag>
        </template>
      </KTable>
    </KPanel>

    <LogDetail
      :id="detailId"
      v-model="detailVisible"
    />
  </div>
</template>

<style scoped>
.k-page {
  padding: 16px;
}
</style>
