<script lang="ts" setup>
import type { ITableColumn } from '@/framework'
import type { ContractEntity } from '@/model/asset/contract/ContractEntity'
import type { ContractDocumentEntity } from '@/model/asset/contract/document/ContractDocumentEntity'
import type { ParticipantEntity } from '@/model/asset/contract/participant/ParticipantEntity'
import { ElLink, ElTable, ElTableColumn, ElTabPane, ElTabs } from 'element-plus'
import { computed } from 'vue'
import KDetail from '@/component/framework/KDetail.vue'
import KDialog from '@/component/framework/KDialog.vue'
import { getCellText, getDictionary, getTableConfigList, WebFileUtil } from '@/framework'
import { KContractService } from '@/hook/asset/useContract'
import { useDetail } from '@/hook/kubo/useDetail'
import { ContractDocumentEntity as ContractDocument } from '@/model/asset/contract/document/ContractDocumentEntity'
import { ParticipantEntity as Participant } from '@/model/asset/contract/participant/ParticipantEntity'

const props = withDefaults(defineProps<{
  modelValue: boolean
  id: number | null
  width?: string
}>(), {
  width: '70%',
})

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
}>()

/**
 * # 合同详情（F4 批 2 · 新底座）
 *
 * 与旧页的三点差别：
 *
 * 1. **数据按 id 重新查**（`useDetail` + `KContractService`）而不是用列表带过来的那份快照 ——
 *    旧页 `DialogProps.withParam(entity)` 拿的是列表里的实体，别人改了状态/参与方就会展示过期数据；
 * 2. 基本信息用 `KDetail`（按 `@Field` 元数据渲染描述列表，字段与列表列走同一套格式化规则）；
 * 3. 参与方 / 附件是两个 `ElTable` 直接渲染**嵌套数组** —— 它们不是独立分页资源，
 *    `KTable` 是服务驱动的（要分页/搜索），嵌套列表用它反而错位。
 *
 * `KDetail` 与这里的 `useDetail` **共用同一个 queryKey**：TanStack 会合并成一次请求，
 * 不会因为"详情页里取两次数据"而多发一次 GET。
 */
const DETAIL_QUERY_KEY = ['asset:contract:detail']

const visible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value),
})

const detail = useDetail(KContractService, {
  queryKey: DETAIL_QUERY_KEY,
  id: computed(() => props.id),
})

const contract = computed<ContractEntity | null>(() => detail.entity.value ?? null)

/** 嵌套表的列直接取实体元数据（`@KTable`），与主表/详情同源：加字段不用改三处 */
const participantColumns = getTableConfigList(Participant)
const documentColumns = getTableConfigList(ContractDocument)

function cellOf(entity: object, column: ITableColumn, EntityClass: object): string {
  return getCellText(entity, column, getDictionary(EntityClass, column.key))
}

/** 静态资源地址（后端存的是相对路径；`WebConfig.staticUrl` 统一前缀） */
function fileUrl(document: ContractDocumentEntity): string {
  return WebFileUtil.getStaticFileUrl(document.url)
}
</script>

<template>
  <KDialog
    v-model="visible"
    :title="contract?.name ? `合同详情 · ${contract.name}` : '合同详情'"
    :width="width"
    hide-footer
    min-height="320px"
  >
    <KDetail
      :id="id"
      :column="2"
      :query-key="DETAIL_QUERY_KEY"
      :service="KContractService"
    />

    <ElTabs class="contract-tabs">
      <ElTabPane :label="`合同参与方（${contract?.participantList?.length ?? 0}）`">
        <ElTable
          :data="contract?.participantList ?? []"
          border
          size="small"
        >
          <ElTableColumn
            v-for="column in participantColumns"
            :key="column.key"
            :label="column.label"
            :min-width="column.minWidth ?? 120"
            :width="column.width"
          >
            <template #default="{ row }">
              {{ cellOf(row as ParticipantEntity, column, Participant) }}
            </template>
          </ElTableColumn>
        </ElTable>
      </ElTabPane>

      <ElTabPane :label="`合同附件（${contract?.documentList?.length ?? 0}）`">
        <ElTable
          :data="contract?.documentList ?? []"
          border
          size="small"
        >
          <ElTableColumn
            v-for="column in documentColumns"
            :key="column.key"
            :label="column.label"
            :min-width="column.minWidth ?? 140"
          >
            <template #default="{ row }">
              <ElLink
                v-if="column.key === 'url'"
                :href="fileUrl(row as ContractDocumentEntity)"
                target="_blank"
              >
                {{ (row as ContractDocumentEntity).name || (row as ContractDocumentEntity).url }}
              </ElLink>
              <span v-else>{{ cellOf(row as ContractDocumentEntity, column, ContractDocument) }}</span>
            </template>
          </ElTableColumn>
        </ElTable>
      </ElTabPane>
    </ElTabs>
  </KDialog>
</template>

<style lang="scss" scoped>
.contract-tabs {
  margin-top: 16px;

  :deep(.el-table__header th) {
    background: var(--el-fill-color-light);
    color: var(--el-text-color-primary);
    font-weight: 600;
  }
}
</style>
