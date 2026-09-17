<script lang="ts" setup>
import { ref } from 'vue'
import KFormDialog from '@/component/framework/KFormDialog.vue'
import KPanel from '@/component/framework/KPanel.vue'
import KTable from '@/component/framework/KTable.vue'
import { Permissions } from '@/config/permissions'
import { KStorageService } from '@/hook/factory/useStorage'
import { useTable } from '@/hook/kubo/useTable'

/**
 * # 仓库（F4 批 1 · 新底座）
 *
 * 仓库是**树形实体**：列表数据来自 `getTreeList`（不分页），所以 `useTable` 开 `tree: true`，
 * `KTable` 自动按 `children` 渲染层级、隐藏分页器，并在行操作里给出「添加子级」。
 *
 * ```diff
 * - import { APanel, ATable, useTableTree } from '@airpower/web'
 * - const hook = useTableTree(StorageService, { editView, beforeAddRow(param, row) { param.parent = row; return param } })
 * + import { useTable } from '@/hook/kubo/useTable'
 * + const table = useTable(KStorageService, { queryKey: ['factory:storage'], tree: true })
 * ```
 *
 * 父级不再靠 `beforeAddRow` 回填：点「添加子级」时 `KTable` 把父行交给页面，
 * 页面把它作为 `preset` 传给 `KFormDialog`（新增时预置 `parentId`，并只读展示上级）。
 */

const table = useTable(KStorageService, { queryKey: ['factory:storage'], tree: true })

const editorVisible = ref(false)
const editingId = ref<number | null>(null)
/** 新增时的预置字段（添加子级要带 parentId） */
const preset = ref<Record<string, unknown>>({})

function openCreate() {
  editingId.value = null
  preset.value = {}
  editorVisible.value = true
}

function openEdit(row: { id: number }) {
  editingId.value = row.id
  preset.value = {}
  editorVisible.value = true
}

function openAddChild(row: { id: number, name?: string, code?: string }) {
  editingId.value = null
  preset.value = { parentId: row.id, parent: row }
  editorVisible.value = true
}

function onSaved() {
  table.reload()
}
</script>

<template>
  <div class="k-page">
    <KPanel
      title="仓库"
      description="按层级维护仓库与库区，支持在任意节点下新增子级"
    >
      <KTable
        :table="table"
        :permissions="{
          add: Permissions.Factory.Storage.add,
          addChild: Permissions.Factory.Storage.addRow,
          edit: Permissions.Factory.Storage.update,
          delete: Permissions.Factory.Storage.delete,
        }"
        @add="openCreate"
        @edit="openEdit"
        @add-child="openAddChild"
      />
    </KPanel>

    <KFormDialog
      :id="editingId"
      v-model="editorVisible"
      :service="KStorageService"
      :preset="preset"
      @saved="onSaved"
    />
  </div>
</template>

<style lang="scss" scoped>
.k-page {
  padding: 16px;
}
</style>
