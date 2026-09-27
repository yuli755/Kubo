<script lang="ts" setup>
import type { RoleEntity } from '@/model/personnel/role/RoleEntity'
import { Search } from '@element-plus/icons-vue'
import { ElButton, ElInput, ElTag, ElTree } from 'element-plus'
import { ref, watch } from 'vue'
import KDialog from '@/component/framework/KDialog.vue'
import { FeedbackUtil, QueryRequest } from '@/framework'
import { KRoleService } from '@/hook/personnel/useRole'
import { KMenuService } from '@/hook/system/useMenu'
import { MenuEntity } from '@/model/system/menu/MenuEntity'

/**
 * # 角色 · 菜单授权（F4 批 3 · 新底座）
 *
 * 与旧版的差别只有「外壳与数据来源」：
 * - 外壳：airpower `ADialog + DialogProps`（靠 `DialogUtil.show` 命令式打开）→ `KDialog + v-model`
 *   （页面自己持有显隐，回调只有 `saved`，不再有 `useEditor` 那层状态包装）；
 * - 数据：`MenuService`（airpower 基类）→ `KMenuService`（框架基类）。
 *
 * **`check-strictly` 必须保留**：菜单树里父节点只是分组，勾父节点不该连带勾选全部子项 ——
 * 旧版就是这个语义（授权精确到每一项），迁移不能顺手改成级联。
 */
const props = defineProps<{
  modelValue: boolean
  /** 被授权的角色 */
  role: RoleEntity | null
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'saved'): void
}>()

const treeRef = ref<InstanceType<typeof ElTree>>()
const treeList = ref<MenuEntity[]>([])
const isLoading = ref(false)
const isSubmitting = ref(false)
const filterKeyword = ref('')
const checkedCount = ref(0)

/** 已勾选的菜单 id：来自角色详情的 `menuList`（后端 getDetail 会带出来） */
function checkedIds(): number[] {
  const ids = (props.role?.menuList ?? []).map(item => item.id)
  checkedCount.value = ids.length
  return ids
}

function onCheckChange() {
  checkedCount.value = treeRef.value?.getCheckedKeys()?.length ?? 0
}

function checkAllMenus() {
  function collectKeys(nodes: MenuEntity[]): number[] {
    const keys: number[] = []
    for (const n of nodes) {
      if (n.id) {
        keys.push(n.id)
      }
      if (n.children?.length) {
        keys.push(...collectKeys(n.children))
      }
    }
    return keys
  }
  const allIds = collectKeys(treeList.value)
  treeRef.value?.setCheckedKeys(allIds)
  checkedCount.value = allIds.length
}

function clearAllMenus() {
  treeRef.value?.setCheckedKeys([])
  checkedCount.value = 0
}

function filterNode(value: string, data: Record<string, unknown>): boolean {
  if (!value)
    return true
  const name = String(data.name ?? '')
  return name.toLowerCase().includes(value.toLowerCase())
}

watch(filterKeyword, (val) => {
  treeRef.value?.filter(val)
})

async function loadTree() {
  isLoading.value = true
  try {
    treeList.value = await new KMenuService().getTreeList(new QueryRequest(MenuEntity))
    checkedCount.value = (props.role?.menuList ?? []).length
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

async function onSubmit() {
  const role = props.role
  if (!role) {
    return
  }
  const checked = (treeRef.value?.getCheckedNodes() ?? []) as MenuEntity[]
  isSubmitting.value = true
  try {
    await new KRoleService().authorizeMenu(role.id, checked)
    FeedbackUtil.toastSuccess('授权菜单成功')
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
    :title="`${role?.name ?? '角色'} · 菜单授权`"
    confirm-text="保存"
    width="540px"
    @update:model-value="value => emit('update:modelValue', value)"
    @cancel="emit('update:modelValue', false)"
    @confirm="onSubmit"
  >
    <div class="k-menu-editor">
      <div class="k-menu-editor__toolbar">
        <ElInput
          v-model="filterKeyword"
          :prefix-icon="Search"
          clearable
          placeholder="搜索菜单名称..."
          size="default"
          class="k-menu-editor__search"
        />
        <div class="k-menu-editor__summary">
          <ElTag
            size="small"
            type="primary"
            effect="plain"
            round
          >
            已选 {{ checkedCount }} 项
          </ElTag>
          <ElButton
            size="small"
            link
            type="primary"
            @click="checkAllMenus"
          >
            全选
          </ElButton>
          <ElButton
            v-if="checkedCount > 0"
            size="small"
            link
            @click="clearAllMenus"
          >
            清空
          </ElButton>
        </div>
      </div>

      <div class="k-menu-editor__tree-wrap">
        <ElTree
          ref="treeRef"
          v-loading="isLoading"
          :data="treeList"
          :default-checked-keys="checkedIds()"
          :props="{ label: 'name', children: 'children' }"
          :filter-node-method="filterNode"
          check-strictly
          default-expand-all
          node-key="id"
          show-checkbox
          @check="onCheckChange"
        />
      </div>
    </div>
  </KDialog>
</template>

<style scoped>
.k-menu-editor {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.k-menu-editor__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.k-menu-editor__search {
  flex: 1;
}

.k-menu-editor__tree-wrap {
  max-height: 420px;
  overflow-y: auto;
  padding: 10px;
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}
</style>
