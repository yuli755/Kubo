<script lang="ts" setup>
import type { PermissionEntity } from '@/model/system/permission/PermissionEntity'
import { ref } from 'vue'
import KFormDialog from '@/component/framework/KFormDialog.vue'
import KPanel from '@/component/framework/KPanel.vue'
import KTable from '@/component/framework/KTable.vue'
import { Permissions } from '@/config/permissions'
import { useTable } from '@/hook/kubo/useTable'
import { KPermissionService } from '@/hook/system/usePermission'

/**
 * # 权限管理（F4 批 6 · 新底座）
 *
 * 与菜单页同构的树表，差别只在**系统内置权限的保护**：
 * `isSystem` 的行不许编辑、不许删除、不许加子级 —— 它们是后端 `@Permission` 注解的镜像，
 * 改了界面就会出现"页面能点、接口 403"的裂缝。三个 `disable-*` 判据都来自行的
 * `isSystem`，页面不自己算。
 */
const table = useTable(KPermissionService, { queryKey: ['system:permission'], tree: true })

const editorVisible = ref(false)
const editingId = ref<number | null>(null)
const preset = ref<Record<string, unknown>>({})

function openCreate() {
  editingId.value = null
  preset.value = {}
  editorVisible.value = true
}

function openEdit(row: PermissionEntity) {
  editingId.value = row.id
  preset.value = {}
  editorVisible.value = true
}

function openAddChild(row: PermissionEntity) {
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
      title="权限管理"
      description="权限标识与后端的 @Permission 注解一一对应；系统内置权限不可改、不可删"
    >
      <KTable
        :table="table"
        :disable-add-child="(row: PermissionEntity) => row.isSystem"
        :disable-delete="(row: PermissionEntity) => row.isSystem"
        :disable-edit="(row: PermissionEntity) => row.isSystem"
        :permissions="{
          add: Permissions.System.Permission.add,
          addChild: Permissions.System.Permission.addRow,
          edit: Permissions.System.Permission.update,
          delete: Permissions.System.Permission.delete,
        }"
        hide-enable
        @add="openCreate"
        @edit="openEdit"
        @add-child="openAddChild"
      >
        <template #cell-identity="{ row }">
          <code
            v-if="(row as PermissionEntity).identity"
            class="font-mono text-xs px-2 py-0.5 rounded bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 border border-slate-200 dark:border-slate-700 select-all"
          >
            {{ (row as PermissionEntity).identity }}
          </code>
          <span
            v-else
            class="text-slate-400"
          >-</span>
        </template>
      </KTable>
    </KPanel>

    <KFormDialog
      :id="editingId"
      v-model="editorVisible"
      :service="KPermissionService"
      :preset="preset"
      width="640px"
      @saved="onSaved"
    >
      <template #extra="{ form }">
        <el-form-item
          v-if="(form as PermissionEntity).parent"
          label="父权限"
        >
          {{ (form as PermissionEntity).parent?.name }}
        </el-form-item>
      </template>
    </KFormDialog>
  </div>
</template>
