<script lang="ts" setup>
import type { StorageEntity } from '@/model/factory/storage/StorageEntity'
import type { StructureEntity } from '@/model/factory/structure/StructureEntity'
import { ElInput, ElRadioButton, ElRadioGroup, ElTree } from 'element-plus'
import { computed, ref, watch } from 'vue'
import KPanel from '@/component/framework/KPanel.vue'
import KTable from '@/component/framework/KTable.vue'
import { Permissions } from '@/config/permissions'
import { KStorageService } from '@/hook/factory/useStorage'
import { KStructureService } from '@/hook/factory/useStructure'
import { useTable } from '@/hook/kubo/useTable'
import { KInventoryService } from '@/hook/wms/useInventory'
import { InventoryTypeEnum } from '@/model/wms/inventory/InventoryTypeEnum'

/**
 * # 库存查询（F4 批 4 · 新底座）
 *
 * 这一页的复杂度全在**两个维度共用一张表**：
 * - 「按仓库」看时数据按仓库聚合，左侧树是仓库树，表格不显示「生产单元」列；
 * - 「按生产单元」看时反过来。
 *
 * 旧实现用 airpower 的 `ATreeBox` + `ATable` + `getTableConfigList` 手写列数组过滤
 * （`tableField = list.filter(item => item.key !== 'storageName')`），
 * 难点是「列」在旧写法里是**页面自己算出来的数组**，与组件的搜索项/单元格规则各算各的。
 * 新写法把这件事收回元数据：KTable 仍然读 `@KTable`，页面只用 `exclude-columns` 说哪列不要，
 * 单元格渲染（数量右对齐、类型染色、`名称(编码)` 关联列）与列表口径自动一致。
 *
 * 筛选走 `table.search(...)`：左侧树点节点 → 带 `storage.id` / `structure.id`；
 * 切维度时会**重建整个筛选条件**（只留 `type`），否则从「按仓库」切到「按生产单元」时
 * 上一次的 `storage.id` 还在，表格会莫名其妙地空掉。
 */
const inventoryType = ref(InventoryTypeEnum.STORAGE.key)

const table = useTable(KInventoryService, {
  queryKey: ['wms:inventory'],
  export: true,
})

// 两棵树各自缓存（切换维度不需要重新请求）；`tree: true` → 走 `getList` 拿嵌套树（后端无 getTreeList 端点）
const storageTable = useTable(KStorageService, { queryKey: ['factory:storage', 'inventoryTree'], tree: true })
const structureTable = useTable(KStructureService, { queryKey: ['factory:structure', 'inventoryTree'], tree: true })

const treeRef = ref<InstanceType<typeof ElTree>>()
const treeKeyword = ref('')

const treeNodes = computed<Array<StorageEntity | StructureEntity>>(() =>
  inventoryType.value === InventoryTypeEnum.STRUCTURE.key
    ? structureTable.list.value as unknown as Array<StorageEntity | StructureEntity>
    : storageTable.list.value as unknown as Array<StorageEntity | StructureEntity>,
)

const treePlaceholder = computed(() =>
  inventoryType.value === InventoryTypeEnum.STRUCTURE.key ? '搜索生产单元' : '搜索仓库',
)

/** 表格列：当前维度下不需要的那一列直接不渲染（后端返回的字段还在，只是不展示） */
const excludeColumns = computed(() =>
  inventoryType.value === InventoryTypeEnum.STRUCTURE.key ? ['storageName'] : ['structureName'],
)

const dimensionKey = computed(() =>
  inventoryType.value === InventoryTypeEnum.STRUCTURE.key ? 'structure' : 'storage',
)

function filterNode(value: string, data: Record<string, unknown>) {
  if (!value) {
    return true
  }
  return String(data.name ?? '').includes(value)
}

watch(treeKeyword, value => treeRef.value?.filter(value))

/** 切换维度：清掉上一次的树筛选与选中态，只保留类型条件 */
function onTypeChanged() {
  treeKeyword.value = ''
  treeRef.value?.setCurrentKey(undefined)
  table.search({ type: inventoryType.value })
}

function onNodeClick(node: StorageEntity | StructureEntity) {
  table.search({ type: inventoryType.value, [dimensionKey.value]: { id: node.id } })
}
</script>

<template>
  <div class="k-page">
    <aside class="k-page__aside">
      <ElRadioGroup
        v-model="inventoryType"
        class="k-page__type"
        size="small"
        @change="onTypeChanged"
      >
        <ElRadioButton
          v-for="view in InventoryTypeEnum.toArray()"
          :key="view.key"
          :value="view.key"
        >
          {{ view.label }}
        </ElRadioButton>
      </ElRadioGroup>

      <ElInput
        v-model="treeKeyword"
        clearable
        :placeholder="treePlaceholder"
      />
      <ElTree
        ref="treeRef"
        :data="treeNodes"
        :filter-node-method="filterNode"
        :props="{ label: 'name', children: 'children' }"
        class="k-page__tree"
        default-expand-all
        highlight-current
        node-key="id"
        @node-click="onNodeClick"
      />
    </aside>

    <KPanel
      title="库存查询"
      description="库存数量由出入库与移库驱动，这里只读；切换维度会同步切换左侧树与表格列"
      class="k-page__main"
    >
      <KTable
        :exclude-columns="excludeColumns"
        :permissions="{ export: Permissions.Wms.Inventory.export }"
        :table="table"
        hide-actions
        hide-add
        show-export
      />
    </KPanel>
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

.k-page__type {
  margin-bottom: 8px;
}

.k-page__tree {
  margin-top: 8px;
}

.k-page__main {
  flex: 1;
  min-width: 0;
}
</style>
