<script lang="ts" setup>
import type { RoleEntity } from '@/model/personnel/role/RoleEntity'
import { ElCheckbox } from 'element-plus'
import { ref } from 'vue'
import KFormDialog from '@/component/framework/KFormDialog.vue'
import KPanel from '@/component/framework/KPanel.vue'
import KTable from '@/component/framework/KTable.vue'
import { Permissions } from '@/config/permissions'
import { WebValidator } from '@/framework'
import { DISABLED_FILTER_DEFAULT, useDisabledVisibility } from '@/hook/kubo/useDisabledVisibility'
import { useTable } from '@/hook/kubo/useTable'
import { KRoleService } from '@/hook/personnel/useRole'
import RoleMenuEditor from './component/menuEditor.vue'
import RolePermissionEditor from './component/permissionEditor.vue'

/**
 * # 角色（F4 批 3 · 新底座）
 *
 * 除了常规 CRUD，角色页有两个**授权动作**，旧版靠 `DialogUtil.show(RoleMenuEditor, role)`
 * 命令式打开弹窗；新底座改成页面持有 `v-model`（能写测试、能静态类型检查，
 * 也免得两个弹窗各要一套 `DialogProps` 契约）。
 *
 * 「菜单 / 权限」两个动作走 `KTable` 的 `:actions`（`RowAction[]`）——
 * 与合同页的「生效 / 终止」同一条路：权限码由页面给，组件不认识业务常量。
 *
 * 旧的 `component/{editor,selector}.vue` 已删除：编辑由 `KFormDialog` 按元数据生成，
 * 角色选择改由调用方（用户编辑弹窗）内嵌 `KSelector`。
 *
 * 旧编辑器有一条自定义校验「角色名不允许带『管理员』三个字」—— 这是业务规则，不能随换底座消失。
 * 现在走 `KFormDialog` 的 `:extra-rules`（框架只合并规则，规则本身属于页面），
 * 与旧写法逐字一致：`WebValidator.show('不允许带管理员三个字').ifContain('管理员')`。
 */

/** 角色名的业务校验：不允许出现「管理员」字样（见文件头说明） */
const roleExtraRules = {
  name: [WebValidator.show('不允许带管理员三个字').ifContain('管理员')],
}

const table = useTable(KRoleService, {
  queryKey: ['personnel:role'],
  defaultFilter: DISABLED_FILTER_DEFAULT,
})

/**
 * 「包含已停用」开关。角色和用户一样开了 `Curd.Enable/Disable`
 * （`SPMS-Server/src/main/java/cn/hamm/spms/module/personnel/role/RoleController.java:21`），
 * 也就一样会踩后端那条刻意的隐形 WHERE（`is_disabled = 0`）：停用后角色从列表消失、无处恢复。
 * 三态约定与取舍见 `@/hook/kubo/useDisabledVisibility` 文件头。
 */
const { includeDisabled, onIncludeDisabledChange } = useDisabledVisibility(table)

const editorVisible = ref(false)
const editingId = ref<number | null>(null)

const menuEditorVisible = ref(false)
const permissionEditorVisible = ref(false)
const targetRole = ref<RoleEntity | null>(null)

function openCreate() {
  editingId.value = null
  editorVisible.value = true
}

function openEdit(row: RoleEntity) {
  editingId.value = row.id
  editorVisible.value = true
}

function openMenuEditor(row: RoleEntity) {
  targetRole.value = row
  menuEditorVisible.value = true
}

function openPermissionEditor(row: RoleEntity) {
  targetRole.value = row
  permissionEditorVisible.value = true
}

function onSaved() {
  table.reload()
}
</script>

<template>
  <div class="k-page">
    <KPanel
      title="角色"
      description="角色定义与授权：菜单（可见范围）与权限（可执行动作）分开授予"
    >
      <KTable
        :actions="[
          {
            label: '菜单',
            permission: Permissions.Personnel.Role.authorizeMenu,
            handler: openMenuEditor,
          },
          {
            label: '权限',
            permission: Permissions.Personnel.Role.authorizePermission,
            handler: openPermissionEditor,
          },
        ]"
        :permissions="{
          add: Permissions.Personnel.Role.add,
          edit: Permissions.Personnel.Role.update,
          delete: Permissions.Personnel.Role.delete,
          enable: Permissions.Personnel.Role.update,
        }"
        :table="table"
        @add="openCreate"
        @edit="openEdit"
      >
        <template #toolbar-left>
          <ElCheckbox
            :model-value="includeDisabled"
            @change="onIncludeDisabledChange"
          >
            包含已停用
          </ElCheckbox>
        </template>
      </KTable>
    </KPanel>

    <KFormDialog
      :id="editingId"
      v-model="editorVisible"
      :extra-rules="roleExtraRules"
      :service="KRoleService"
      title="角色"
      @saved="onSaved"
    />

    <RoleMenuEditor
      v-model="menuEditorVisible"
      :role="targetRole"
      @saved="onSaved"
    />

    <RolePermissionEditor
      v-model="permissionEditorVisible"
      :role="targetRole"
      @saved="onSaved"
    />
  </div>
</template>
