<script lang="ts" setup>
import type { OperationLogItem } from '@/api/system/types'
import { ElAlert, ElButton, ElEmpty, ElInput, ElOption, ElPagination, ElSelect, ElTable, ElTableColumn, ElTag } from 'element-plus'
import { computed, ref, watch } from 'vue'
import KPanel from '@/component/framework/KPanel.vue'
import { useOperationLogReport } from '@/composables/useOperationLogReport'
import { DateTimeFormatter, DateTimeUtil } from '@/framework'
import LogDetail from './detail.vue'

/**
 * # 操作审计（`operation_log`）
 *
 * ## 这一页看的是「改了什么」，不是「调了什么接口」
 * 菜单叫「操作日志」，历史上这里对接的是一个 **airpower 时代的请求日志模块**（`log/getPage`、
 * `log/getDetail`、字段 ip / request / response / pendingTime）。那个模块在后端**从来没有实现过**：
 * 没有 `@Api("log")` 控制器、没有实体、没有表，请求过去只有 404。这一页因此长期是一个
 * 「点得开、永远空」的页面。
 *
 * 后端真正实现了的是**操作审计**：`operation_log` 表 + `OperationLogRecorder`（业务线程取身份、
 * 异步落库）+ `GET /dataResource/operation-logs`（最多 200 条），并且已经被
 * `useOperationLogReport()` 与数据资源页复用。所以这里的做法是**换数据源、不新造后端**：
 * 复用现成接口与 composable，把三份死代码（LogService / LogEntity / useLog）删掉。
 *
 * ## 覆盖面要照实说
 * 审计是 opt-in 的：只有实体标了 `@OperationLog` 才会写（当前只有物料主数据）。
 * 表空 ≠ 没人改过数据，所以空态文案直接把这句话写出来，避免把「没接审计」读成「审计干净」。
 *
 * ## 为什么筛选与分页都在前端
 * 接口一次最多返回最近 200 条、且不分页；后端只支持按 `resourceKey` 再筛一次。
 * 与其为这一页新开一个分页接口（要新增权限码、新增路由、还要过契约守卫），
 * 不如把「最近 200 条」的边界**显示在页面上**，需要更久远的记录时走保留期策略与 SQL。
 */
const report = useOperationLogReport()

/** 后端 `findTop200ByOrderByIdDesc()` 的硬上限；等于这个数时说明可能被截断 */
const MAX_ROWS = 200

const resourceKey = ref('')
const action = ref('')
const keyword = ref('')
const pageNum = ref(1)
const pageSize = ref(20)

/**
 * 动作词表。必须与后端 `OperationLogRecorder.ACTION_*` 常量一一对应
 * （由 `src/config/__tests__/backendRegressionPhase3.spec.ts` 机械对账）。
 *
 * ENABLE/DISABLE/PUBLISH 是独立动作而不是 UPDATE 的别名：单据类资源排除了 Delete，
 * 「停用」就是用户退役一条数据的唯一手段；「发布」则不可逆地冻结该行。
 * 这两件事都必须能独立追责，混进 UPDATE 就分不出「改了备注」和「停用了供应商」。
 */
const ACTION_LABEL: Record<string, string> = {
  CREATE: '新增',
  UPDATE: '修改',
  DELETE: '删除',
  ENABLE: '启用',
  DISABLE: '停用',
  PUBLISH: '发布',
}
const ACTION_TYPE: Record<string, 'danger' | 'info' | 'success' | 'warning'> = {
  CREATE: 'success',
  UPDATE: 'warning',
  DELETE: 'danger',
  ENABLE: 'success',
  DISABLE: 'info',
  PUBLISH: 'warning',
}

/** 资源下拉的选项从当前结果里取：不硬编码资源清单，后端多接一个实体这里就自动多一项 */
const resourceKeys = computed(() => Array.from(new Set(report.items.map(item => item.resourceKey))).sort())

const filtered = computed<OperationLogItem[]>(() => report.items.filter((item) => {
  if (resourceKey.value && item.resourceKey !== resourceKey.value) {
    return false
  }
  if (action.value && item.action !== action.value) {
    return false
  }
  const text = keyword.value.trim().toLowerCase()
  if (!text) {
    return true
  }
  return [item.targetLabel, item.targetId, item.userId, item.tenantId, item.resourceKey]
    .some(value => String(value ?? '').toLowerCase().includes(text))
}))

const paged = computed(() => filtered.value.slice((pageNum.value - 1) * pageSize.value, pageNum.value * pageSize.value))

// 筛选条件一变就回到第一页：否则「筛完发现是空的」其实只是停在超出范围的页码上
watch([resourceKey, action, keyword], () => {
  pageNum.value = 1
})

const detailVisible = ref(false)
const detailItem = ref<OperationLogItem | null>(null)

function openDetail(row: OperationLogItem) {
  detailItem.value = row
  detailVisible.value = true
}

async function refresh() {
  await report.refresh()
}
</script>

<template>
  <div class="k-page">
    <KPanel
      title="操作审计"
      description="谁在何时把哪条记录改成了什么（写入方是标了 @OperationLog 的实体，异步落库）"
    >
      <div class="log-list__toolbar">
        <ElSelect
          v-model="resourceKey"
          class="log-list__filter"
          clearable
          placeholder="全部资源"
        >
          <ElOption
            v-for="key in resourceKeys"
            :key="key"
            :label="key"
            :value="key"
          />
        </ElSelect>
        <ElSelect
          v-model="action"
          class="log-list__filter log-list__filter--narrow"
          clearable
          placeholder="全部动作"
        >
          <!--
            选项直接遍历 ACTION_LABEL：动作下拉若写成硬编码的 <ElOption>，
            后端多一个 ACTION_* 常量时筛选器就会静默漏掉它（数据在表格里显示成
            裸英文码，筛选下拉却选不出来）。由词表驱动 ⇒ 只有一个地方要改。
          -->
          <ElOption
            v-for="(label, key) in ACTION_LABEL"
            :key="key"
            :label="`${label}（${key}）`"
            :value="key"
          />
        </ElSelect>
        <ElInput
          v-model="keyword"
          class="log-list__filter"
          clearable
          placeholder="搜索目标 / 操作者 / 资源"
        />
        <ElButton
          :loading="report.loading"
          @click="refresh"
        >
          刷新
        </ElButton>
        <span class="log-list__count">
          筛出 {{ filtered.length }} / 共 {{ report.count }} 条
          <template v-if="report.count >= MAX_ROWS">
            （已到后端 200 条上限，可能不含更早的记录）
          </template>
        </span>
      </div>

      <ElAlert
        v-if="report.failed"
        class="log-list__alert"
        type="warning"
        :closable="false"
        show-icon
        title="未能取得操作审计"
        description="刷新本页或检查 /dataResource/operation-logs 是否可用；取不到结果时不会显示为「没人改过」。"
      />

      <ElTable
        v-loading="report.loading"
        :data="paged"
        size="default"
        stripe
        border
        @row-dblclick="openDetail"
      >
        <ElTableColumn
          type="index"
          label="#"
          width="56"
        />
        <ElTableColumn
          label="时间"
          width="200"
        >
          <template #default="{ row }">
            <span :title="DateTimeUtil.getFriendlyDateTime((row as OperationLogItem).createTime)">
              {{ DateTimeFormatter.FULL_DATE_TIME.formatMilliSecond((row as OperationLogItem).createTime) }}
            </span>
          </template>
        </ElTableColumn>
        <ElTableColumn
          label="动作"
          width="100"
        >
          <template #default="{ row }">
            <ElTag
              size="small"
              :type="ACTION_TYPE[(row as OperationLogItem).action] ?? 'info'"
              effect="plain"
            >
              {{ ACTION_LABEL[(row as OperationLogItem).action] ?? (row as OperationLogItem).action }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn
          label="资源"
          width="180"
        >
          <template #default="{ row }">
            <code class="log-list__code">{{ (row as OperationLogItem).resourceKey }}</code>
          </template>
        </ElTableColumn>
        <ElTableColumn
          label="目标"
          min-width="200"
        >
          <template #default="{ row }">
            {{ (row as OperationLogItem).targetLabel ?? '-' }}
            <span class="log-list__muted">#{{ (row as OperationLogItem).targetId ?? '-' }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn
          label="操作者"
          width="100"
        >
          <template #default="{ row }">
            {{ (row as OperationLogItem).userId ?? '-' }}
          </template>
        </ElTableColumn>
        <ElTableColumn
          label="租户"
          width="90"
        >
          <template #default="{ row }">
            {{ (row as OperationLogItem).tenantId ?? '-' }}
          </template>
        </ElTableColumn>
        <ElTableColumn
          label="快照"
          width="130"
        >
          <template #default="{ row }">
            <ElTag
              size="small"
              type="info"
              effect="plain"
            >
              变更前 {{ (row as OperationLogItem).beforeSnapshot ? '有' : '无' }}
            </ElTag>
            <ElTag
              size="small"
              type="info"
              effect="plain"
              class="log-list__tag-gap"
            >
              变更后 {{ (row as OperationLogItem).afterSnapshot ? '有' : '无' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn
          label="操作"
          width="110"
          fixed="right"
        >
          <template #default="{ row }">
            <ElButton
              link
              type="primary"
              @click="openDetail(row as OperationLogItem)"
            >
              查看变更
            </ElButton>
          </template>
        </ElTableColumn>

        <template #empty>
          <ElEmpty
            :image-size="80"
            :description="report.count
              ? '当前筛选条件下没有记录（清空筛选可看到全部已取到的审计）'
              : '还没有操作审计记录：只有标了 @OperationLog 的实体才会写入（当前仅物料主数据），其它模块的改动不会出现在这里'"
          />
        </template>
      </ElTable>

      <div class="log-list__pager">
        <ElPagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[20, 50, 100]"
          :total="filtered.length"
          layout="total, sizes, prev, pager, next"
          background
          small
        />
      </div>
    </KPanel>

    <LogDetail
      v-model="detailVisible"
      :item="detailItem"
    />
  </div>
</template>

<style scoped>
.k-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
}

.log-list__toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.log-list__filter {
  width: 220px;
}

.log-list__filter--narrow {
  width: 160px;
}

.log-list__count {
  margin-left: auto;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.log-list__alert {
  margin-bottom: 12px;
}

.log-list__code {
  padding: 1px 6px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  background: var(--el-fill-color-light);
  font-family: var(--el-font-family-mono, monospace);
  font-size: 12px;
}

.log-list__muted {
  margin-left: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.log-list__tag-gap {
  margin-left: 4px;
}

.log-list__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
