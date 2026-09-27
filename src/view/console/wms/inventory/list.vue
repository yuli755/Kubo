<script lang="ts" setup>
import type { StorageEntity } from '@/model/factory/storage/StorageEntity'
import type { StructureEntity } from '@/model/factory/structure/StructureEntity'
import { RefreshLeft, Search } from '@element-plus/icons-vue'
import { ElButton, ElInput, ElRadioButton, ElRadioGroup, ElTag, ElTree } from 'element-plus'
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
const selectedNode = ref<StorageEntity | StructureEntity | null>(null)

const table = useTable(KInventoryService, {
  queryKey: ['wms:inventory'],
  export: true,
})

// 两棵树各自缓存（切换维度不需要重新请求）；`tree: true` → `getTreeList` 拿嵌套树
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
  selectedNode.value = null
  treeKeyword.value = ''
  treeRef.value?.setCurrentKey(undefined)
  table.search({ type: inventoryType.value })
}

const inventorySummary = computed(() => {
  const list = table.list.value as Array<{ quantity?: number }>
  const totalSku = list.length
  const totalQty = list.reduce((acc, row) => acc + (Number(row.quantity) || 0), 0)
  const lowStockCount = list.filter(row => (Number(row.quantity) || 0) < 10).length
  return { totalSku, totalQty, lowStockCount }
})

function onNodeClick(node: StorageEntity | StructureEntity) {
  selectedNode.value = node
  table.search({ type: inventoryType.value, [dimensionKey.value]: { id: node.id } })
}

function clearNodeFilter() {
  selectedNode.value = null
  treeRef.value?.setCurrentKey(undefined)
  table.search({ type: inventoryType.value })
}
</script>

<template>
  <div class="k-page">
    <aside class="k-page__aside">
      <div class="k-page__aside-header">
        <span class="k-page__aside-title">库存维度</span>
        <ElTag
          v-if="selectedNode"
          size="small"
          closable
          type="primary"
          effect="plain"
          @close="clearNodeFilter"
        >
          {{ selectedNode.name }}
        </ElTag>
      </div>

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
        :prefix-icon="Search"
        :placeholder="treePlaceholder"
      />

      <div class="k-page__tree-wrap">
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
      </div>

      <ElButton
        v-if="selectedNode"
        class="k-page__aside-reset"
        link
        :icon="RefreshLeft"
        @click="clearNodeFilter"
      >
        查看全部库存
      </ElButton>
    </aside>

    <KPanel
      title="库存查询"
      description="库存数量由出入库与移库驱动，这里只读；切换维度会同步切换左侧树与表格列"
      class="k-page__main"
    >
      <div class="inventory-stats-bar">
        <div class="stat-pill">
          <span class="stat-label">在库物料 SKU：</span>
          <span class="stat-val font-mono">{{ inventorySummary.totalSku }}</span>
          <span class="stat-unit">种</span>
        </div>
        <div class="stat-pill">
          <span class="stat-label">总库存件数：</span>
          <span class="stat-val font-mono">{{ inventorySummary.totalQty.toLocaleString() }}</span>
          <span class="stat-unit">件</span>
        </div>
        <div
          v-if="inventorySummary.lowStockCount > 0"
          class="stat-pill is-warning"
        >
          <span class="stat-label">低库存预警（&lt; 10 件）：</span>
          <span class="stat-val font-mono">{{ inventorySummary.lowStockCount }}</span>
          <span class="stat-unit">项</span>
        </div>
      </div>

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
  width: 100%;
}

.k-page__aside {
  flex: 0 0 240px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.03);
}

.k-page__aside-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.k-page__aside-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.k-page__type {
  width: 100%;
  display: flex;

  :deep(.el-radio-button) {
    flex: 1;

    .el-radio-button__inner {
      width: 100%;
      padding: 6px 0;
    }
  }
}

.k-page__tree-wrap {
  max-height: 480px;
  overflow-y: auto;
}

.k-page__tree {
  margin: 4px 0;
}

.k-page__aside-reset {
  margin-top: 4px;
}

.k-page__main {
  flex: 1;
  min-width: 0;
}

.inventory-stats-bar {
  display: flex;
  gap: 16px;
  align-items: center;
  margin-bottom: 12px;
  padding: 8px 14px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  flex-wrap: wrap;

  .stat-pill {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: 13px;
    color: var(--el-text-color-regular);

    .stat-val {
      font-weight: 700;
      color: var(--el-color-primary);
    }

    .stat-unit {
      color: var(--el-text-color-secondary);
      font-size: 12px;
    }

    &.is-warning .stat-val {
      color: var(--el-color-warning-dark-2);
    }
  }
}
</style>
