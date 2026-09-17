<script lang="ts" setup>
import type { DepartmentEntity } from '@/model/personnel/department/DepartmentEntity'
import type { RoleEntity } from '@/model/personnel/role/RoleEntity'
import { ElButton, ElInput, ElMessage, ElTag, ElTree } from 'element-plus'
import { computed, onMounted, ref, watch } from 'vue'
import KFormDialog from '@/component/framework/KFormDialog.vue'
import KPanel from '@/component/framework/KPanel.vue'
import KSelector from '@/component/framework/KSelector.vue'
import KTable from '@/component/framework/KTable.vue'
import { useMaskPolicy } from '@/composables/useMaskPolicy'
import { Permissions } from '@/config/permissions'
import { useTable } from '@/hook/kubo/useTable'
import { KDepartmentService } from '@/hook/personnel/useDepartment'
import { KRoleService } from '@/hook/personnel/useRole'
import { KUserService } from '@/hook/personnel/useUser'

/**
 * # 用户（F4 批 3 · 新底座）
 *
 * 这一页比其它列表多两块东西，迁移时各有各的坑：
 *
 * 1. **左侧部门树筛选**：旧版是 airpower `ATreeBox` + `hook.request.value.filter.departmentId`
 *    的写法（直接改传输层对象）。新底座用一棵 `ElTree`（数据来自 `KDepartmentService.getTreeList`，
 *    走 TanStack 缓存）+ `table.search({ departmentId })` —— 页面拿到的仍是"筛选条件"这一个概念，
 *    不再需要知道请求体长什么样。
 * 2. **角色 / 部门多选**：它们不是 `@Form` 字段（实体上是 `RoleEntity[]` / `DepartmentEntity[]`），
 *    所以走 `KFormDialog` 的 `#extra` 插槽自己渲染标签 + `KSelector`（多选、部门带 `tree`）。
 *    选中结果写回 `form` 对象（弹窗交出来的同一个引用），随提交体一起走。
 *
 * 提交前把两个列表裁成"只带 id"（旧版 `copyOnlyId()` 的同一语义）：后端只需要关联对象的 id，
 * 带上整行数据会被当成"要改关联实体"。裁剪放在 `beforeSubmit`，与脱敏写守卫同一条路。
 */
const { blockedSubmit, maskedFieldsIn, load } = useMaskPolicy('personnel.user', ['idCard', 'phone', 'email'])
onMounted(() => load())

const table = useTable(KUserService, { queryKey: ['personnel:user'] })

/** 部门树（筛选用；与部门页共享同一接口，但缓存键分开，避免把"筛选树"和"部门页"的查询搅在一起） */
const departmentTree = useTable(KDepartmentService, { queryKey: ['personnel:department', 'userFilter'], tree: true })
const departmentNodes = departmentTree.list

const treeRef = ref<InstanceType<typeof ElTree>>()
const treeKeyword = ref('')

function filterNode(value: string, data: Record<string, unknown>) {
  if (!value) {
    return true
  }
  return String(data.name ?? '').includes(value)
}

watch(treeKeyword, (value) => {
  treeRef.value?.filter(value)
})

function selectDepartment(node: DepartmentEntity) {
  table.search({ ...table.filter.value, departmentId: node.id })
}

function clearDepartment() {
  const rest = { ...table.filter.value }
  delete rest.departmentId
  table.search(rest)
  treeRef.value?.setCurrentKey(undefined)
}

// ── 编辑弹窗 ──
const editorVisible = ref(false)
const editingId = ref<number | null>(null)
const form = ref<Record<string, unknown>>({})
const rolePicking = ref(false)
const departmentPicking = ref(false)

function openCreate() {
  editingId.value = null
  editorVisible.value = true
}

function openEdit(row: { id: number }) {
  editingId.value = row.id
  editorVisible.value = true
}

/**
 * 表单初始化：补齐两个"非元数据字段"的默认值。
 * 新增时 `KFormDialog` 只按 `@Form` 字段重置，`roleList` / `departmentList` 还是 undefined，
 * 直接 `v-for` 会炸 —— 这里补空数组（同一个对象引用，改动即提交体）。
 */
function onLoaded(next: Record<string, unknown>) {
  next.roleList ??= []
  next.departmentList ??= []
  form.value = next
}

const roleList = computed(() => (form.value.roleList ?? []) as RoleEntity[])
const departmentList = computed(() => (form.value.departmentList ?? []) as DepartmentEntity[])

function pickRoles(rows: RoleEntity[]) {
  form.value.roleList = rows
  rolePicking.value = false
}

function pickDepartments(rows: DepartmentEntity[]) {
  form.value.departmentList = rows
  departmentPicking.value = false
}

function removeRole(index: number) {
  roleList.value.splice(index, 1)
}

function removeDepartment(index: number) {
  departmentList.value.splice(index, 1)
}

function beforeSubmit(payload: Record<string, unknown>) {
  const warning = blockedSubmit(payload)
  if (warning) {
    ElMessage.warning(warning)
    return null
  }
  payload.roleList = (payload.roleList as Array<{ copyOnlyId: () => unknown }> ?? []).map(item => item.copyOnlyId())
  payload.departmentList = (payload.departmentList as Array<{ copyOnlyId: () => unknown }> ?? []).map(item => item.copyOnlyId())
  return payload
}

function onSaved() {
  table.reload()
}
</script>

<template>
  <div class="k-page">
    <aside class="k-page__aside">
      <ElInput
        v-model="treeKeyword"
        clearable
        placeholder="搜索部门"
      />
      <ElTree
        ref="treeRef"
        :data="departmentNodes"
        :filter-node-method="filterNode"
        :props="{ label: 'name', children: 'children' }"
        class="k-page__tree"
        default-expand-all
        highlight-current
        node-key="id"
        @node-click="selectDepartment"
      />
      <ElButton
        link
        @click="clearDepartment"
      >
        查看全部
      </ElButton>
    </aside>

    <KPanel
      title="用户"
      description="用户档案与角色/部门归属；手机、邮箱、身份证受列级脱敏策略管控"
      class="k-page__main"
    >
      <KTable
        :permissions="{
          add: Permissions.Personnel.User.add,
          edit: Permissions.Personnel.User.update,
          delete: Permissions.Personnel.User.delete,
          enable: Permissions.Personnel.User.update,
        }"
        :table="table"
        @add="openCreate"
        @edit="openEdit"
      />
    </KPanel>

    <KFormDialog
      :id="editingId"
      v-model="editorVisible"
      :before-submit="beforeSubmit"
      :columns="2"
      :service="KUserService"
      title="用户"
      width="640px"
      @loaded="onLoaded"
      @saved="onSaved"
    >
      <template #extra>
        <div
          v-if="maskedFieldsIn(form).length"
          class="mask-tip"
        >
          字段 {{ maskedFieldsIn(form).join('、') }} 当前按数据权限策略显示为脱敏值，
          保持不动即可保留原值（提交掩码不会覆盖真实数据）
        </div>

        <div class="block">
          <div class="block__head">
            <span class="block__title">角色</span>
            <ElButton
              link
              type="primary"
              @click="rolePicking = true"
            >
              添加角色
            </ElButton>
          </div>
          <ElTag
            v-for="(role, index) in roleList"
            :key="role.id"
            class="block__tag"
            closable
            size="large"
            @close="removeRole(index)"
          >
            {{ role.name }}
          </ElTag>
        </div>

        <div class="block">
          <div class="block__head">
            <span class="block__title">部门</span>
            <ElButton
              link
              type="primary"
              @click="departmentPicking = true"
            >
              添加部门
            </ElButton>
          </div>
          <ElTag
            v-for="(department, index) in departmentList"
            :key="department.id"
            class="block__tag"
            closable
            size="large"
            @close="removeDepartment(index)"
          >
            {{ department.name }}
          </ElTag>
        </div>
      </template>
    </KFormDialog>

    <KSelector
      v-model="rolePicking"
      :service="KRoleService"
      :selected="roleList"
      multiple
      title="选择角色"
      @confirm="pickRoles"
    />

    <KSelector
      v-model="departmentPicking"
      :service="KDepartmentService"
      :selected="departmentList"
      multiple
      title="选择部门"
      tree
      @confirm="pickDepartments"
    />
  </div>
</template>

<style lang="scss" scoped>
.k-page {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  padding: 16px;
}

.k-page__aside {
  flex: 0 0 240px;
  padding: 12px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
}

.k-page__tree {
  margin: 8px 0;
}

.k-page__main {
  flex: 1;
  min-width: 0;
}

.block {
  margin-top: 8px;
}

.block__head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.block__title {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.block__tag {
  margin: 4px 6px 0 0;
}

.mask-tip {
  padding: 4px 0 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
