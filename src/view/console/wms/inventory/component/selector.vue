<script lang="ts" setup>
import type { IDialogActionProps, ITableColumn } from '@/framework'
import type { StorageEntity } from '@/model/factory/storage/StorageEntity'
import type { StructureEntity } from '@/model/factory/structure/StructureEntity'
import type { InventoryEntity } from '@/model/wms/inventory/InventoryEntity'
import { RefreshLeft, Search } from '@element-plus/icons-vue'
import { ElButton, ElEmpty, ElInput, ElRadioButton, ElRadioGroup, ElTable, ElTableColumn, ElTag, ElTree } from 'element-plus'
import { computed, ref, watch } from 'vue'
import KDialog from '@/component/framework/KDialog.vue'
import {
  getCellColor,
  getCellText,
  getColumnAlign,
  getDictionary,
  getTableConfigList,
  QueryRequest,
} from '@/framework'
import { KStorageService } from '@/hook/factory/useStorage'
import { KStructureService } from '@/hook/factory/useStructure'
import { KInventoryService } from '@/hook/wms/useInventory'
import { StorageEntity as Storage } from '@/model/factory/storage/StorageEntity'
import { StructureEntity as Structure } from '@/model/factory/structure/StructureEntity'
import { InventoryEntity as Inventory } from '@/model/wms/inventory/InventoryEntity'
import { InventoryTypeEnum } from '@/model/wms/inventory/InventoryTypeEnum'

/**
 * # 库存选择器（F4 批 4 · 新底座）
 *
 * 被移库单（挑来源库存）与出库「确认出库」（挑扣减来源）打开。
 *
 * ## 为什么不是 `KSelector`
 * `KSelector` 是「一个服务 + 一张表」的通用选择器；库存选择器多一层**筛选树**：
 * 按仓库维度看时左树是仓库树、按生产单元维度看时是生产单元树，而且切换维度要同时换树与筛选条件。
 * 这是业务形态，不是通用能力 —— 所以保留独立组件。
 *
 * ## 契约：`IDialogActionProps`（与旧版一致）
 * 旧版是 airpower 的 `DialogProps.withSelector`，宿主 `DialogUtil.select(InventorySelector, param)`
 * 拿到选中的库存。新底座沿用同一份 props 契约（`param` / `selectList` / `isMultiple` /
 * `onConfirm` / `onCancel`），因此**还没迁移的页面继续用 `@airpower/web` 的 `DialogUtil` 打开它也不会坏**
 * —— 过渡期的旧页面（如 channel 的销售出库）与已迁移页面共用同一个选择器。
 * 同时也支持声明式（`v-model` + `@confirm`），与 `KDialog` 的两种用法一致。
 *
 * ## 一条保留的判据
 * **禁用库存不可选**（`isDisabled`）：旧版把行内「选择」按钮置灰，这里同样禁掉；
 * 多选场景下由后端在提交时再拦一道（选择器只做前端提示）。
 */
const props = withDefaults(defineProps<IDialogActionProps<InventoryEntity> & {
  /** 显隐（声明式用法）；命令式打开时不传，默认即打开 */
  modelValue?: boolean
}>(), {
  modelValue: true,
})

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'confirm', data: InventoryEntity | InventoryEntity[]): void
  (event: 'cancel'): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value),
})

const isMultiple = computed(() => !!props.isMultiple)
const selected = ref<InventoryEntity[]>([...(props.selectList ?? []) as InventoryEntity[]])

const inventoryType = ref(props.param?.type ?? InventoryTypeEnum.STORAGE.key)
const list = ref<InventoryEntity[]>([])
const isLoading = ref(false)
const isLoadingTree = ref(false)
const treeNodes = ref<Array<StorageEntity | StructureEntity>>([])
const treeRef = ref<InstanceType<typeof ElTree>>()
const treeKeyword = ref('')
const selectedNode = ref<StorageEntity | StructureEntity | undefined>(undefined)

const columns = computed<ITableColumn[]>(() => getTableConfigList(Inventory))

const treePlaceholder = computed(() =>
  inventoryType.value === InventoryTypeEnum.STRUCTURE.key ? '搜索生产单元...' : '搜索仓库...',
)

async function loadTree() {
  isLoadingTree.value = true
  try {
    if (inventoryType.value === InventoryTypeEnum.STRUCTURE.key) {
      treeNodes.value = await new KStructureService().getTreeList(new QueryRequest(Structure))
    }
    else {
      treeNodes.value = await new KStorageService().getTreeList(new QueryRequest(Storage))
    }
  }
  finally {
    isLoadingTree.value = false
  }
}

async function loadList() {
  isLoading.value = true
  try {
    const request = new QueryRequest(Inventory)
    const filter: Record<string, unknown> = { type: inventoryType.value }
    if (selectedNode.value?.id) {
      // 与旧版同形：筛选条件只带关联对象的 id
      filter[inventoryType.value === InventoryTypeEnum.STRUCTURE.key ? 'structure' : 'storage'] = { id: selectedNode.value.id }
    }
    request.filter = filter as unknown as InventoryEntity
    list.value = await new KInventoryService().getList(request)
  }
  finally {
    isLoading.value = false
  }
}

function filterNode(value: string, data: Record<string, unknown>) {
  if (!value) {
    return true
  }
  return String(data.name ?? '').includes(value)
}

function onTypeChanged() {
  selectedNode.value = undefined
  treeKeyword.value = ''
  treeRef.value?.setCurrentKey(undefined)
  void loadTree()
  void loadList()
}

function onNodeClick(node: StorageEntity | StructureEntity) {
  selectedNode.value = node
  void loadList()
}

function onClearNode() {
  selectedNode.value = undefined
  treeRef.value?.setCurrentKey(undefined)
  void loadList()
}

function onSelectionChange(rows: InventoryEntity[]) {
  selected.value = rows
  if (isMultiple.value) {
    props.onCallback?.()
  }
}

function pick(row: InventoryEntity) {
  emit('confirm', row)
  visible.value = false
}

function onMultiConfirm() {
  emit('confirm', selected.value)
  visible.value = false
}

function onCancel() {
  emit('cancel')
  visible.value = false
}

function cellText(row: InventoryEntity, column: ITableColumn): string {
  return getCellText(row, column, getDictionary(Inventory, column.key))
}

function cellColor(row: InventoryEntity, column: ITableColumn) {
  return getCellColor(getDictionary(Inventory, column.key), (row as unknown as Record<string, unknown>)[column.key])
}

watch(treeKeyword, value => treeRef.value?.filter(value))

void loadTree()
void loadList()
</script>

<template>
  <KDialog
    :disable-confirm="isMultiple && selected.length === 0"
    :hide-footer="!isMultiple"
    :model-value="visible"
    confirm-text="确定"
    title="选择库存"
    width="70%"
    @cancel="onCancel"
    @confirm="onMultiConfirm"
    @update:model-value="value => visible = value"
  >
    <div class="inventory-selector">
      <aside class="inventory-selector__aside">
        <div class="inventory-selector__aside-header">
          <span class="inventory-selector__aside-title">库存维度</span>
          <ElTag
            v-if="selectedNode"
            size="small"
            closable
            type="primary"
            effect="plain"
            @close="onClearNode"
          >
            {{ selectedNode.name }}
          </ElTag>
        </div>

        <ElRadioGroup
          v-model="inventoryType"
          size="small"
          class="inventory-selector__type-group"
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

        <div class="inventory-selector__tree-wrap">
          <ElTree
            ref="treeRef"
            v-loading="isLoadingTree"
            :data="treeNodes"
            :filter-node-method="filterNode"
            :props="{ label: 'name', children: 'children' }"
            class="inventory-selector__tree"
            default-expand-all
            highlight-current
            node-key="id"
            @node-click="onNodeClick"
          />
        </div>

        <ElButton
          v-if="selectedNode"
          link
          :icon="RefreshLeft"
          class="inventory-selector__reset-btn"
          @click="onClearNode"
        >
          查看全部库存
        </ElButton>
      </aside>

      <div class="inventory-selector__main">
        <!-- R53 反馈修复：多选模式下没勾选时「确定」是禁用的 —— 旁边给出计数与操作指引，
             不让按钮静默失效 -->
        <div
          v-if="isMultiple"
          class="inventory-selector__hint"
        >
          已勾选 {{ selected.length }} 项，勾选表格行后点「确定」回填
        </div>
        <ElTable
          v-loading="isLoading"
          :data="list"
          border
          stripe
          class="inventory-selector__table"
          @selection-change="onSelectionChange"
          @row-dblclick="row => !isMultiple && !(row as InventoryEntity).isDisabled && pick(row as InventoryEntity)"
        >
          <ElTableColumn
            v-if="isMultiple"
            type="selection"
            width="46"
            :reserve-selection="true"
          />
          <ElTableColumn
            v-for="column in columns"
            :key="column.key"
            :prop="column.key"
            :label="column.label"
            :width="column.width"
            :min-width="column.minWidth ?? 120"
            :align="getColumnAlign(column)"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              <span
                v-if="column.key === 'quantity'"
                class="inventory-selector__quantity"
              >
                {{ cellText(row as InventoryEntity, column) }}
              </span>
              <span
                v-else
                :style="cellColor(row as InventoryEntity, column) ? { color: cellColor(row as InventoryEntity, column) } : {}"
              >
                {{ cellText(row as InventoryEntity, column) }}
              </span>
            </template>
          </ElTableColumn>
          <ElTableColumn
            v-if="!isMultiple"
            label="操作"
            width="90"
            align="center"
          >
            <template #default="{ row }">
              <!-- permission-exempt: 选择器确认只回填表单，不构成数据写入 -->
              <ElButton
                link
                type="primary"
                :disabled="(row as InventoryEntity).isDisabled"
                @click="pick(row as InventoryEntity)"
              >
                选择
              </ElButton>
            </template>
          </ElTableColumn>
          <template #empty>
            <ElEmpty
              description="没有符合条件的库存"
              :image-size="64"
            />
          </template>
        </ElTable>
      </div>
    </div>
  </KDialog>
</template>

<style lang="scss" scoped>
.inventory-selector {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.inventory-selector__aside {
  flex: 0 0 240px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
}

.inventory-selector__aside-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.inventory-selector__aside-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.inventory-selector__type-group {
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

.inventory-selector__tree-wrap {
  max-height: 360px;
  overflow-y: auto;
}

.inventory-selector__tree {
  margin: 4px 0;
}

.inventory-selector__reset-btn {
  margin-top: 4px;
}

.inventory-selector__main {
  flex: 1;
  min-width: 0;
}

/* R53：多选勾选计数提示（禁用的「确定」按钮旁要能看懂为什么点不动） */
.inventory-selector__hint {
  margin-bottom: 8px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}

.inventory-selector__table {
  width: 100%;
  border-radius: var(--kubo-radius-sm, 4px);
  overflow: hidden;

  :deep(.el-table__header th) {
    background: var(--el-fill-color-light);
    color: var(--el-text-color-primary);
    font-weight: 600;
  }
}

.inventory-selector__quantity {
  font-weight: 600;
  color: var(--el-color-primary);
}
</style>
