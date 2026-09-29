<script lang="ts" setup>
import type { OperationLogItem } from '@/api/system/types'
import type { IJson } from '@/framework'
import { ElEmpty, ElTable, ElTableColumn, ElTabPane, ElTabs, ElTag, ElTree } from 'element-plus'
import { computed } from 'vue'
import KDialog from '@/component/framework/KDialog.vue'
import { ClipboardUtil, DateTimeFormatter } from '@/framework'

/**
 * # 操作审计详情（变更前后对比）
 *
 * ## 为什么这里不发请求
 * 列表页拿到的 `OperationLogItem` 已经**带着** `beforeSnapshot` / `afterSnapshot` 全文
 * （`GET /dataResource/operation-logs` 一次返回完整字段），所以详情只是同一份数据的另一种排版。
 * 后端没有「按 id 取单条审计」的路由 —— 旧页面按 id 重新拉详情，是因为它对接的是一个
 * **后端从未实现**的请求日志模块（`log/getDetail` 只会 404）。少一次请求，也少一处会烂的约定。
 *
 * ## 三个页签的分工
 * - 字段变更 Diff：默认页签，只列**有变化**的标量字段（`before` → `after`）；
 * - 变更前 / 变更后快照：原文按树展开，点叶子可复制 —— 排查「谁把它改回去的」时要看全量。
 *
 * ## 快照为空是正常的，不是坏了
 * `CREATE` 没有 before、`DELETE` 没有 after（后端 `OperationLogSnapshot` 只序列化标量字段）。
 * 这时对应页签显示空态说明，而不是渲染一棵空树让人以为取数失败。
 */
const props = defineProps<{
  /** 显隐（父页面 `v-model`） */
  modelValue?: boolean
  /** 列表里选中的那一条审计（null 表示未选择） */
  item?: OperationLogItem | null
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
}>()

interface ITreeData {
  key: string
  label: string
  value: string
  children?: ITreeData[]
}

/** 把一层 JSON 对象摊成树的节点（对象继续下钻，标量成为叶子） */
function getTreeData(source: IJson, prefix = ''): ITreeData[] {
  return Object.keys(source).map((key, index) => {
    const value = source[key]
    const id = `${prefix}${index}`
    if (value !== null && typeof value === 'object') {
      return { key: id, label: key, value: '', children: getTreeData(value as IJson, `${id}-`) }
    }
    return { key: id, label: key, value: String(value ?? ''), children: [] }
  })
}

function safeParse(jsonString?: string | null): IJson {
  if (!jsonString) {
    return {}
  }
  try {
    return (JSON.parse(jsonString) as IJson) || {}
  }
  catch {
    return { error: '数据格式异常，无法解析' }
  }
}

const log = computed<OperationLogItem | null>(() => props.item ?? null)
const beforeJson = computed(() => safeParse(log.value?.beforeSnapshot))
const afterJson = computed(() => safeParse(log.value?.afterSnapshot))
const beforeTree = computed(() => getTreeData(beforeJson.value))
const afterTree = computed(() => getTreeData(afterJson.value))

/** CREATE 没有变更前快照、DELETE 没有变更后快照 —— 空态要照实说，不要让人以为取数失败 */
const hasBefore = computed(() => Object.keys(beforeJson.value).length > 0)
const hasAfter = computed(() => Object.keys(afterJson.value).length > 0)

interface IDiffItem {
  field: string
  beforeVal: string
  afterVal: string
  status: 'added' | 'removed' | 'modified'
}

/**
 * 字段变更 Diff：对 `before` / `after` 两份快照取键的并集，只留**有变化**的标量字段。
 *
 * 只比标量是跟着后端走的：`OperationLogSnapshot` 本来就只序列化标量字段，
 * 快照里出现的对象/数组是序列化残留，比了也只会得到一整段 JSON 文本的差异，没有可读性。
 */
const diffList = computed<IDiffItem[]>(() => {
  const before = beforeJson.value
  const after = afterJson.value
  const keys = Array.from(new Set([...Object.keys(before), ...Object.keys(after)]))
  return keys
    .filter(key => key !== 'error' && typeof before[key] !== 'object' && typeof after[key] !== 'object')
    .map<IDiffItem>((key) => {
      const beforeRaw = before[key]
      const afterRaw = after[key]
      const beforeVal = beforeRaw === undefined ? '-' : String(beforeRaw ?? '')
      const afterVal = afterRaw === undefined ? '-' : String(afterRaw ?? '')
      let status: IDiffItem['status'] = 'modified'
      if (beforeRaw === undefined && afterRaw !== undefined) {
        status = 'added'
      }
      else if (beforeRaw !== undefined && afterRaw === undefined) {
        status = 'removed'
      }
      return { field: key, beforeVal, afterVal, status }
    })
    .filter(item => item.status !== 'modified' || item.beforeVal !== item.afterVal)
})

/**
 * 动作词表，必须与 `list.vue` 及后端 `OperationLogRecorder.ACTION_*` 一致
 * （由 `src/config/__tests__/backendRegressionPhase3.spec.ts` 机械对账）。
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

const treeProps = { label: 'label', children: 'children' }

function close(value: boolean) {
  emit('update:modelValue', value)
}

async function copy(value: string) {
  if (value) {
    await ClipboardUtil.copy(value)
  }
}
</script>

<template>
  <KDialog
    :model-value="!!modelValue"
    :title="log ? `操作审计 · ${ACTION_LABEL[log.action] ?? log.action} · ${log.targetLabel ?? `#${log.targetId ?? '-'}`}` : '操作审计'"
    confirm-text="关闭"
    hide-cancel
    min-height="60%"
    width="900px"
    @update:model-value="close"
    @confirm="close(false)"
  >
    <template v-if="log">
      <div class="log-detail__meta">
        <ElTag
          size="small"
          :type="ACTION_TYPE[log.action] ?? 'info'"
          effect="plain"
        >
          {{ ACTION_LABEL[log.action] ?? log.action }}
        </ElTag>
        <ElTag
          size="small"
          type="info"
          effect="plain"
        >
          资源：{{ log.resourceKey }}
        </ElTag>
        <ElTag
          size="small"
          type="info"
          effect="plain"
        >
          时间：{{ DateTimeFormatter.FULL_DATE_TIME.formatMilliSecond(log.createTime) }}
        </ElTag>
        <ElTag
          size="small"
          type="info"
          effect="plain"
        >
          操作者：{{ log.userId ?? '-' }}
        </ElTag>
        <ElTag
          size="small"
          type="info"
          effect="plain"
        >
          租户：{{ log.tenantId ?? '-' }}
        </ElTag>
        <ElTag
          size="small"
          type="info"
          effect="plain"
        >
          目标 ID：{{ log.targetId ?? '-' }}
        </ElTag>
      </div>

      <ElTabs type="border-card">
        <ElTabPane :label="`字段变更 Diff（${diffList.length}）`">
          <div
            v-if="diffList.length > 0"
            class="log-detail__diff"
          >
            <ElTable
              :data="diffList"
              size="small"
              stripe
              border
            >
              <ElTableColumn
                prop="field"
                label="变动字段"
                width="180"
              />
              <ElTableColumn
                label="类型"
                width="100"
              >
                <template #default="{ row }">
                  <ElTag
                    v-if="(row as IDiffItem).status === 'added'"
                    size="small"
                    type="success"
                  >
                    新增字段
                  </ElTag>
                  <ElTag
                    v-else-if="(row as IDiffItem).status === 'removed'"
                    size="small"
                    type="danger"
                  >
                    字段消失
                  </ElTag>
                  <ElTag
                    v-else
                    size="small"
                    type="warning"
                  >
                    内容变更
                  </ElTag>
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="beforeVal"
                label="变更前"
              />
              <ElTableColumn
                prop="afterVal"
                label="变更后"
              />
            </ElTable>
          </div>
          <ElEmpty
            v-else
            description="标量字段没有差异（纯新增/删除，或本次修改只动了非快照字段）"
            :image-size="60"
          />
        </ElTabPane>

        <ElTabPane label="变更前快照">
          <ElTree
            v-if="hasBefore"
            :data="beforeTree"
            :props="treeProps"
            node-key="key"
            default-expand-all
          >
            <template #default="{ node, data }">
              <span
                class="log-detail__node log-detail__node--copyable"
                @click="copy((data as ITreeData).value)"
              >
                <span class="log-detail__key">{{ node.label }}</span>
                <span class="log-detail__value">{{ (data as ITreeData).value }}</span>
              </span>
            </template>
          </ElTree>
          <ElEmpty
            v-else
            description="这条是新增（CREATE），没有变更前快照"
            :image-size="60"
          />
        </ElTabPane>

        <ElTabPane label="变更后快照">
          <ElTree
            v-if="hasAfter"
            :data="afterTree"
            :props="treeProps"
            node-key="key"
            default-expand-all
          >
            <template #default="{ node, data }">
              <span
                class="log-detail__node log-detail__node--copyable"
                @click="copy((data as ITreeData).value)"
              >
                <span class="log-detail__key">{{ node.label }}</span>
                <span class="log-detail__value">{{ (data as ITreeData).value }}</span>
              </span>
            </template>
          </ElTree>
          <ElEmpty
            v-else
            description="这条是删除（DELETE），没有变更后快照"
            :image-size="60"
          />
        </ElTabPane>
      </ElTabs>
    </template>
  </KDialog>
</template>

<style scoped>
.log-detail__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.log-detail__node {
  display: flex;
  flex: 1;
  align-items: center;
  width: 0;
}

.log-detail__node--copyable {
  cursor: pointer;
}

.log-detail__key {
  color: var(--el-text-color-secondary);
}

.log-detail__value {
  flex: 1;
  width: 0;
  margin-left: 20px;
  overflow: hidden;
  text-align: right;
  text-overflow: ellipsis;
}

.log-detail__diff {
  padding: 4px 0;
}
</style>
