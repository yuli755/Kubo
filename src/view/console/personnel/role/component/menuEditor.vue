<script lang="ts" setup>
import type { RoleEntity } from '@/model/personnel/role/RoleEntity'
import { ElTree } from 'element-plus'
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

/** 已勾选的菜单 id：来自角色详情的 `menuList`（后端 getDetail 会带出来） */
function checkedIds(): number[] {
  return (props.role?.menuList ?? []).map(item => item.id)
}

async function loadTree() {
  isLoading.value = true
  try {
    treeList.value = await new KMenuService().getTreeList(new QueryRequest(MenuEntity))
  }
  finally {
    isLoading.value = false
  }
}

watch(
  () => props.modelValue,
  async (opened) => {
    if (opened) {
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
    @update:model-value="value => emit('update:modelValue', value)"
    @cancel="emit('update:modelValue', false)"
    @confirm="onSubmit"
  >
    <ElTree
      ref="treeRef"
      v-loading="isLoading"
      :data="treeList"
      :default-checked-keys="checkedIds()"
      :props="{ label: 'name', children: 'children' }"
      check-strictly
      default-expand-all
      node-key="id"
      show-checkbox
    />
  </KDialog>
</template>
