<script lang="ts" setup>
import type { RoleEntity } from '@/model/personnel/role/RoleEntity'
import { Search } from '@element-plus/icons-vue'
import { ElButton, ElInput, ElTable, ElTableColumn, ElTag } from 'element-plus'
import { computed, nextTick, ref, watch } from 'vue'
import KDialog from '@/component/framework/KDialog.vue'
import { FeedbackUtil, getEnumLabel, QueryRequest } from '@/framework'
import { KRoleService } from '@/hook/personnel/useRole'
import { KPermissionService } from '@/hook/system/usePermission'
import { PermissionEntity } from '@/model/system/permission/PermissionEntity'
import { PermissionTypeEnum } from '@/model/system/permission/PermissionTypeEnum'

/**
 * # 角色 · 权限授权（F4 批 3 · 新底座）
 *
 * 旧版用 airpower `ATable` 渲染权限树并靠 `@selected` 收集勾选；新底座这里用
 * Element Plus 的 `ElTable` 树表 + 多选列 —— 不换成 `KTable` 的理由：
 * `KTable` 的列来自 `@KTable` 元数据，而权限实体属 **system 域**（最后一批才迁）。
 * 为了一个弹窗去给系统域实体加元数据、再改它的页面，是把批次边界搞糊；
 * 这里只需要"名称 / 标识 / 类型"三列，手写列更清楚，也不影响将来系统域换 `KTable`。
 *
 * 回显按 id 勾选：`role.permissionList` 来自角色详情，权限树加载完成后逐行 `toggleRowSelection`。
 */
const props = defineProps<{
  modelValue: boolean
  role: RoleEntity | null
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'saved'): void
}>()

interface IElTableInstance {
  toggleRowSelection: (row: unknown, selected?: boolean) => void
  clearSelection: () => void
}

const tableRef = ref<IElTableInstance>()
const treeList = ref<PermissionEntity[]>([])
const isLoading = ref(false)
const isSubmitting = ref(false)
const selected = ref<PermissionEntity[]>([])
const filterKeyword = ref('')

const checkedIds = computed(() => new Set((props.role?.permissionList ?? []).map(item => item.id)))

/** 树形权限 → 拍平成列表（回显勾选时用；ElTable 的多选 API 只认行对象） */
function flatten(nodes: PermissionEntity[]): PermissionEntity[] {
  return nodes.flatMap(node => [node, ...flatten(node.children ?? [])])
}

const displayTreeList = computed(() => {
  if (!filterKeyword.value.trim()) {
    return treeList.value
  }
  const kw = filterKeyword.value.trim().toLowerCase()
  const filterNodes = (nodes: PermissionEntity[]): PermissionEntity[] => {
    const res: PermissionEntity[] = []
    for (const node of nodes) {
      const matchSelf = (node.name && node.name.toLowerCase().includes(kw))
        || (node.identity && node.identity.toLowerCase().includes(kw))
      const matchedChildren = node.children ? filterNodes(node.children) : []
      if (matchSelf || matchedChildren.length > 0) {
        const copy = Object.assign(new PermissionEntity(), node)
        if (matchedChildren.length > 0) {
          copy.children = matchedChildren
        }
        res.push(copy)
      }
    }
    return res
  }
  return filterNodes(treeList.value)
})

async function loadTree() {
  isLoading.value = true
  try {
    treeList.value = await new KPermissionService().getTreeList(new QueryRequest(PermissionEntity))
    await nextTick()
    // 先清空再回显：弹窗会被反复打开，残留的勾选必须丢掉（否则会悄悄多授权限）
    tableRef.value?.clearSelection()
    for (const row of flatten(treeList.value)) {
      if (checkedIds.value.has(row.id)) {
        tableRef.value?.toggleRowSelection(row, true)
      }
    }
  }
  finally {
    isLoading.value = false
  }
}

watch(
  () => props.modelValue,
  async (opened) => {
    if (opened) {
      filterKeyword.value = ''
      await loadTree()
    }
  },
  { immediate: true },
)

function typeLabel(type: number): string {
  return getEnumLabel(PermissionTypeEnum, type) ?? '-'
}

function clearAllSelection() {
  tableRef.value?.clearSelection()
  selected.value = []
}

async function onSubmit() {
  const role = props.role
  if (!role) {
    return
  }
  isSubmitting.value = true
  try {
    await new KRoleService().authorizePermission(role.id, selected.value)
    FeedbackUtil.toastSuccess('角色权限授权成功')
    emit('saved')
  }
  finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <KDialog
    :model-value="modelValue"
    :disable-confirm="isSubmitting"
    :title="`${role?.name ?? '角色'} · 权限授权`"
    confirm-text="保存"
    width="74%"
    @update:model-value="value => emit('update:modelValue', value)"
    @cancel="emit('update:modelValue', false)"
    @confirm="onSubmit"
  >
    <div class="k-permission-editor">
      <div class="k-permission-editor__toolbar">
        <ElInput
          v-model="filterKeyword"
          :prefix-icon="Search"
          clearable
          placeholder="搜索权限名称或标识..."
          class="k-permission-editor__search"
        />
        <div class="k-permission-editor__summary">
          <ElTag
            size="small"
            type="primary"
            effect="plain"
            round
          >
            已选定 {{ selected.length }} 项权限
          </ElTag>
          <ElButton
            v-if="selected.length"
            size="small"
            link
            type="primary"
            @click="clearAllSelection"
          >
            清空已选
          </ElButton>
        </div>
      </div>

      <div class="k-permission-editor__table-wrap">
        <ElTable
          ref="tableRef"
          v-loading="isLoading"
          :data="displayTreeList"
          :tree-props="{ children: 'children' }"
          default-expand-all
          border
          stripe
          max-height="460"
          row-key="id"
          class="k-permission-table"
          @selection-change="rows => selected = rows as PermissionEntity[]"
        >
          <ElTableColumn
            type="selection"
            width="46"
            :reserve-selection="true"
          />
          <ElTableColumn
            label="权限名称"
            min-width="190"
            prop="name"
          />
          <ElTableColumn
            label="权限标识"
            min-width="240"
            prop="identity"
          >
            <template #default="{ row }">
              <span class="font-mono text-identity">{{ row.identity }}</span>
            </template>
          </ElTableColumn>
          <ElTableColumn
            label="类型"
            width="110"
            align="center"
          >
            <template #default="{ row }">
              <ElTag
                disable-transitions
                size="small"
                effect="light"
              >
                {{ typeLabel(row.type) }}
              </ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn
            label="系统内置"
            width="100"
            align="center"
          >
            <template #default="{ row }">
              <ElTag
                v-if="row.isSystem"
                disable-transitions
                size="small"
                type="warning"
                effect="plain"
              >
                是
              </ElTag>
              <span
                v-else
                class="text-muted"
              >-</span>
            </template>
          </ElTableColumn>
        </ElTable>
      </div>
    </div>
  </KDialog>
</template>

<style scoped>
.k-permission-editor {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.k-permission-editor__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.k-permission-editor__search {
  max-width: 320px;
}

.k-permission-editor__summary {
  display: flex;
  align-items: center;
  gap: 10px;
}

.k-permission-editor__table-wrap {
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
}

.text-identity {
  color: var(--el-color-primary-dark-2);
  font-size: 13px;
}

.text-muted {
  color: var(--el-text-color-placeholder);
}
</style>
