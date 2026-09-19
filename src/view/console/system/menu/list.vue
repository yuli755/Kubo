<script lang="ts" setup>
import type { MenuEntity } from '@/model/system/menu/MenuEntity'
import { ElLink } from 'element-plus'
import { ref } from 'vue'
import KFormDialog from '@/component/framework/KFormDialog.vue'
import KPanel from '@/component/framework/KPanel.vue'
import KTable from '@/component/framework/KTable.vue'
import { Permissions } from '@/config/permissions'
import { useTable } from '@/hook/kubo/useTable'
import { KMenuService } from '@/hook/system/useMenu'

/**
 * # 菜单管理（F4 批 6 · 新底座）
 *
 * 树表（`useTable({ tree: true })` → 后端 `getList` 的树形返回），KTable 自己渲染层级与
 * 「添加子级」按钮 —— 旧页面的 `useTableTree` + `ATable is-tree show-add-row` 是同一件事。
 *
 * 三个行为口径照抄旧页面（它们都是**业务规则**，不是样式）：
 * - **有子菜单的行不许删**（`disable-delete`），否则一次删掉一棵子树；
 * - **新增子菜单时把父级放进 `preset`**（旧页面用 `beforeAddRow` 改 `param.parent`）——
 *   父级关系是提交体的一部分，用户在表单里看不到也不该手填；
 * - 路径列渲染成链接（点开就是该菜单对应的路由）。
 */
const table = useTable(KMenuService, { queryKey: ['system:menu'], tree: true })

const editorVisible = ref(false)
const editingId = ref<number | null>(null)
const preset = ref<Record<string, unknown>>({})

function openCreate() {
  editingId.value = null
  preset.value = {}
  editorVisible.value = true
}

function openEdit(row: MenuEntity) {
  editingId.value = row.id
  preset.value = {}
  editorVisible.value = true
}

/** 新增子菜单：把父级塞进提交体（`parentId` 由后端读取，`parent` 让表单显示"父菜单"） */
function openAddChild(row: MenuEntity) {
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
      title="菜单管理"
      description="菜单 / 路由 / 组件地址的层级维护：改完刷新页面即可看到导航变化"
    >
      <KTable
        :table="table"
        :actions="[]"
        :disable-delete="(row: MenuEntity) => (row.children?.length ?? 0) > 0"
        :permissions="{
          add: Permissions.System.Menu.add,
          addChild: Permissions.System.Menu.addRow,
          edit: Permissions.System.Menu.update,
          delete: Permissions.System.Menu.delete,
        }"
        hide-enable
        @add="openCreate"
        @edit="openEdit"
        @add-child="openAddChild"
      >
        <template #cell-path="{ row }">
          <ElLink
            v-if="row.path"
            type="primary"
            :href="row.path"
          >
            {{ row.path }}
          </ElLink>
          <span v-else>-</span>
        </template>
      </KTable>
    </KPanel>

    <KFormDialog
      :id="editingId"
      v-model="editorVisible"
      :service="KMenuService"
      :preset="preset"
      width="640px"
      @saved="onSaved"
    >
      <template #extra="{ form }">
        <el-form-item
          v-if="(form as MenuEntity).parent"
          label="父菜单"
        >
          {{ (form as MenuEntity).parent?.name }}
        </el-form-item>
      </template>
    </KFormDialog>
  </div>
</template>

<style scoped>
.k-page {
  padding: 16px;
}
</style>
