<script lang="ts" setup>
import { ElButton, ElFormItem, ElTag } from 'element-plus'
import { ref } from 'vue'
import KFormDialog from '@/component/framework/KFormDialog.vue'
import KPanel from '@/component/framework/KPanel.vue'
import KTable from '@/component/framework/KTable.vue'
import { Permissions } from '@/config/permissions'
import { DialogUtil } from '@/framework'
import { KStructureService } from '@/hook/factory/useStructure'
import { useTable } from '@/hook/kubo/useTable'
import { DepartmentSelector } from '@/view/console/personnel/department/component'

/**
 * # 生产单元（F4 批 1 · 新底座）
 *
 * 与仓库页同为树表（`tree: true`），差别在表单里多一块**「所属部门」多选**：
 * 它不是 `@Form` 字段（实体上只是一个 `DepartmentEntity[]`），所以走 `KFormDialog` 的
 * `#extra` 插槽自己渲染。
 *
 * 部门选择器仍是存量的 airpower 组件（部门域尚未迁移）：用 `@/framework` 的 `DialogUtil.selectList`
 * 打开它 —— 那份实现的 props/events 与 airpower 的 `DialogProps` 契约一致，可以混用；
 * 等部门域迁完再换成 `KSelector`。
 */

const table = useTable(KStructureService, { queryKey: ['factory:structure'], tree: true })

const editorVisible = ref(false)
const editingId = ref<number | null>(null)
const preset = ref<Record<string, unknown>>({})
/**
 * 弹窗当前的表单对象（`@loaded` 回传）。
 * 「所属部门」不是 `@Form` 字段，但它是提交体的一部分，所以页面要在表单之外读写它 ——
 * 拿到的是弹窗内部同一个对象引用，改了就等于改了提交内容。
 */
const form = ref<Record<string, unknown>>({})

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

const departments = () => (form.value.departmentList ?? []) as Array<{ id: number, name: string }>

async function selectDepartment() {
  const selected = await DialogUtil.selectList(DepartmentSelector as never, departments() as never)
  form.value.departmentList = selected
}

function removeDepartment(index: number) {
  const list = [...departments()]
  list.splice(index, 1)
  form.value.departmentList = list
}

function onSaved() {
  table.reload()
}
</script>

<template>
  <div class="k-page">
    <KPanel
      title="生产单元"
      description="工位/工区层级维护，可挂多个所属部门"
    >
      <KTable
        :table="table"
        :permissions="{
          add: Permissions.Factory.Structure.add,
          addChild: Permissions.Factory.Structure.addRow,
          edit: Permissions.Factory.Structure.update,
          delete: Permissions.Factory.Structure.delete,
        }"
        @add="openCreate"
        @edit="openEdit"
        @add-child="openAddChild"
      />
    </KPanel>

    <KFormDialog
      :id="editingId"
      v-model="editorVisible"
      :service="KStructureService"
      :preset="preset"
      @saved="onSaved"
      @loaded="form = $event"
    >
      <template #extra>
        <ElFormItem label="所属部门">
          <div class="department-list">
            <ElButton
              v-permission="[Permissions.Factory.Structure.add, Permissions.Factory.Structure.update]"
              @click="selectDepartment()"
            >
              添加部门
            </ElButton>
            <ElTag
              v-for="(department, index) in departments()"
              :key="department.id"
              closable
              size="large"
              @close="removeDepartment(index)"
            >
              {{ department.name }}
            </ElTag>
          </div>
        </ElFormItem>
      </template>
    </KFormDialog>
  </div>
</template>

<style lang="scss" scoped>
.k-page {
  padding: 16px;
}

.department-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
</style>
