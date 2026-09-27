<script lang="ts" setup>
import { ElAlert } from 'element-plus'
import { ref } from 'vue'
import KPanel from '@/component/framework/KPanel.vue'
import KTable from '@/component/framework/KTable.vue'
import { useInertPolicyReport } from '@/composables/useInertPolicyReport'
import { Permissions } from '@/config/permissions'
import { useTable } from '@/hook/kubo/useTable'
import { DataFieldPolicyService } from '@/hook/system/useDataFieldPolicy'
import DataFieldPolicyEditor from './component/editor.vue'

// 存量盘点：哪些行是"配了但没人读"的动作。判定在后端，这里只负责列出来。
const { count: inertCount, preview: inertPreview, failed: inertFailed } = useInertPolicyReport()

const table = useTable(DataFieldPolicyService, { queryKey: ['system:datapermission:fieldPolicy'] })

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
</script>

<template>
  <KPanel
    title="列级脱敏策略"
    description="配置字段级敏感数据的动态脱敏与导出授权规则，保障不同主体的数据安全"
  >
    <ElAlert
      v-if="inertCount"
      class="inert-warning"
      type="warning"
      :closable="false"
      show-icon
      title="有已启用的列级策略，其动作不会被列级遮蔽读到"
      :description="`${inertCount} 条（${inertPreview.join('、')}）。这类行保存后会留在表里、计入「已配置」，但不改变任何输出：要么停用，要么到「数据权限-行级策略」按行级方式配置。`"
    />
    <ElAlert
      v-else-if="inertFailed"
      class="inert-warning"
      type="info"
      :closable="false"
      show-icon
      title="未能取得存量盘点结果"
      description="刷新本页或检查 /dataResource/inert-policies 是否可用；取不到结果时不会显示为「没有孤儿策略」。"
    />
    <KTable
      :table="table"
      :permissions="{
        add: Permissions.System.DataFieldPolicy.add,
        edit: Permissions.System.DataFieldPolicy.update,
        delete: Permissions.System.DataFieldPolicy.delete,
      }"
      @add="openCreate"
      @edit="openEdit"
    />

    <DataFieldPolicyEditor
      :id="editingId"
      v-model="editorVisible"
      @saved="onSaved"
    />
  </KPanel>
</template>

<style lang="scss" scoped>
.inert-warning {
  margin-bottom: 8px;
}
</style>
