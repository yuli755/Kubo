<script lang="ts" setup>
import { ref } from 'vue'
import KFormDialog from '@/component/framework/KFormDialog.vue'
import KPanel from '@/component/framework/KPanel.vue'
import KTable from '@/component/framework/KTable.vue'
import { Permissions } from '@/config/permissions'
import { useTable } from '@/hook/kubo/useTable'
import { KDepartmentService } from '@/hook/personnel/useDepartment'

/**
 * # 部门（F4 批 3 · 新底座）
 *
 * 部门是树形实体，页面结构与 `factory/storage` 同型：`useTable({ tree: true })` +
 * `KTable`（自动渲染层级、隐藏分页器、行内给「添加子级」）+ `KFormDialog`。
 *
 * ```diff
 * - import { APanel, ATable, useTableTree } from '@airpower/web'
 * - const hook = useTableTree(DepartmentService, { editView, beforeAddRow(param, row) { param.parent = row; return param } })
 * + import { useTable } from '@/hook/kubo/useTable'
 * + const table = useTable(KDepartmentService, { queryKey: ['personnel:department'], tree: true })
 * ```
 *
 * 两处 airpower 才需要的写法顺手消失了：
 * 1. `disable-delete="(row) => row.children.length > 0"` —— 「有子级不能删」已是 `KTable` 的
 *    内置判据（树模式删父节点会留下孤儿），不再由页面重复声明；
 * 2. `beforeAddRow` 回填父级 —— 改由页面在「添加子级」时把父行作为 `preset` 传给弹窗。
 *
 * 原 `component/` 下的 `editor.vue`（airpower 表单）与 `selector.vue`（airpower 选择器）
 * 迁移后删除：编辑改由元数据驱动的 `KFormDialog`，选择改由 `KSelector` 直接内嵌
 * （调用方是用户编辑弹窗与生产单元页）。**部门这张表本身没有"启用/禁用"动作**，
 * 所以不传 `permissions.enable`（`KTable` 不传就不渲染那个按钮）。
 */

const table = useTable(KDepartmentService, { queryKey: ['personnel:department'], tree: true })

const editorVisible = ref(false)
const editingId = ref<number | null>(null)
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
      title="部门"
      description="按层级维护组织架构，支持在任意节点下新增子级"
    >
      <KTable
        :permissions="{
          add: Permissions.Personnel.Department.add,
          addChild: Permissions.Personnel.Department.addRow,
          edit: Permissions.Personnel.Department.update,
          delete: Permissions.Personnel.Department.delete,
        }"
        :table="table"
        @add="openCreate"
        @edit="openEdit"
        @add-child="openAddChild"
      />
    </KPanel>

    <KFormDialog
      :id="editingId"
      v-model="editorVisible"
      :preset="preset"
      :service="KDepartmentService"
      title="部门"
      @saved="onSaved"
    />
  </div>
</template>

<style lang="scss" scoped>
.k-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
}
</style>
