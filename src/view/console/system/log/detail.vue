<script lang="ts" setup>
import type { IJson } from '@/framework'
import type { LogEntity } from '@/model/system/log/LogEntity'
import { ElEmpty, ElTable, ElTableColumn, ElTabPane, ElTabs, ElTag, ElTree } from 'element-plus'
import { computed } from 'vue'
import KDialog from '@/component/framework/KDialog.vue'
import { ClipboardUtil } from '@/framework'
import { useDetail } from '@/hook/kubo/useDetail'
import { LogService } from '@/hook/system/useLog'

/**
 * # 操作日志详情（F4 批 6 · 新底座）
 *
 * 请求体与响应体都是**JSON 字符串**，这里解析成树展示 —— 与旧实现同一套 `getTreeData`，
 * 差别只在「数据从哪来」与「谁控制显隐」：
 *
 * - 旧：`DialogUtil.show(Detail, row)` 把**列表里那一行**当数据源（命令式 + 快照）；
 * - 新：`useDetail(LogService, { id })` 按 id 重新拉（列表里的 `request`/`response`
 *   在列表接口里是被裁掉的，日志详情只能重新查），显隐由父页面 `v-model` 控制。
 *
 * 解析失败不抛错：`safeParse` 返回一条错误说明 —— 日志查看器不该因为一条脏数据白屏。
 */
const props = defineProps<{
  /** 显隐（父页面 `v-model`） */
  modelValue?: boolean
  /** 日志 id（null 表示未选择） */
  id?: number | null
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
}>()

const detail = useDetail(LogService, {
  queryKey: ['system:log'],
  id: computed(() => props.id ?? null),
})

const log = computed<LogEntity | undefined>(() => detail.entity.value)

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
      const result: ITreeData = { key: id, label: key, value: '', children: getTreeData(value as IJson, `${id}-`) }
      return result
    }
    return { key: id, label: key, value: String(value ?? ''), children: [] }
  })
}

function safeParse(jsonString?: string): IJson {
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

const requestTree = computed(() => getTreeData(safeParse(log.value?.request)))
const responseTree = computed(() => getTreeData(safeParse(log.value?.response)))

interface IDiffItem {
  field: string
  requestVal: string
  responseVal: string
  status: 'modified' | 'added' | 'same'
}

const diffList = computed<IDiffItem[]>(() => {
  const req = safeParse(log.value?.request)
  const res = safeParse(log.value?.response)
  if (!req || typeof req !== 'object' || !res || typeof res !== 'object') {
    return []
  }

  const keys = Array.from(new Set([...Object.keys(req), ...Object.keys(res)]))
  return keys
    .filter(k => k !== 'error' && typeof req[k] !== 'object' && typeof res[k] !== 'object')
    .map((field) => {
      const rVal = req[field] !== undefined ? String(req[field]) : '-'
      const respVal = res[field] !== undefined ? String(res[field]) : '-'
      let status: IDiffItem['status'] = 'same'
      if (req[field] === undefined && res[field] !== undefined) {
        status = 'added'
      }
      else if (req[field] !== undefined && res[field] !== undefined && String(req[field]) !== String(res[field])) {
        status = 'modified'
      }
      return {
        field,
        requestVal: rVal,
        responseVal: respVal,
        status,
      }
    })
    .filter(item => item.status !== 'same')
})

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
    :title="log?.action ? `请求详情 · ${log.action}` : '请求详情'"
    confirm-text="关闭"
    hide-cancel
    min-height="60%"
    width="900px"
    @update:model-value="close"
    @confirm="close(false)"
  >
    <div
      v-if="log"
      class="log-detail__meta"
    >
      <ElTag
        size="small"
        type="info"
        effect="plain"
      >
        用户：{{ log.userId ?? '-' }}
      </ElTag>
      <ElTag
        size="small"
        type="info"
        effect="plain"
      >
        IP：{{ log.ip ?? '-' }}
      </ElTag>
      <ElTag
        size="small"
        type="info"
        effect="plain"
      >
        版本：{{ log.version ?? '-' }}
      </ElTag>
      <ElTag
        size="small"
        :type="((log.updateTime ?? 0) - (log.createTime ?? 0)) > 500 ? 'warning' : 'success'"
        effect="plain"
      >
        耗时：{{ (log.updateTime ?? 0) - (log.createTime ?? 0) }}ms
      </ElTag>
    </div>

    <ElTabs type="border-card">
      <ElTabPane label="请求">
        <ElTree
          :data="requestTree"
          :props="treeProps"
          node-key="key"
          default-expand-all
        >
          <template #default="{ node, data }">
            <span class="log-detail__node">
              <span class="log-detail__key">{{ node.label }}</span>
              <span class="log-detail__value">{{ (data as ITreeData).value }}</span>
            </span>
          </template>
        </ElTree>
      </ElTabPane>

      <ElTabPane label="响应">
        <ElTree
          :data="responseTree"
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
      </ElTabPane>

      <ElTabPane label="字段变更 Diff">
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
              width="160"
            />
            <ElTableColumn
              label="类型"
              width="100"
            >
              <template #default="{ row }">
                <ElTag
                  v-if="row.status === 'added'"
                  size="small"
                  type="success"
                >
                  新增字段
                </ElTag>
                <ElTag
                  v-else-if="row.status === 'modified'"
                  size="small"
                  type="warning"
                >
                  内容变更
                </ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn
              prop="requestVal"
              label="请求提交值"
            />
            <ElTableColumn
              prop="responseVal"
              label="响应结果值"
            />
          </ElTable>
        </div>
        <ElEmpty
          v-else
          description="无标量字段差异（纯读取或未产生数据变动）"
          :image-size="60"
        />
      </ElTabPane>
    </ElTabs>
  </KDialog>
</template>

<style scoped>
.log-detail__meta {
  display: flex;
  gap: 16px;
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
