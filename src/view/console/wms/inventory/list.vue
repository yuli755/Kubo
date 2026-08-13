<script lang="ts" setup>
import type { ITree, QueryPage, RootEntity } from '@airpower/web'
import {
  APage,
  APanel,
  ATable,
  ATreeBox,
  getTableConfigList,
  QueryRequest,
  QueryRequestPage,
  QueryResponsePage,
} from '@airpower/web'
import { computed, ref, watch } from 'vue'
import { useInventoryList } from '@/hook/wms/useInventory'
import { StorageEntity } from '@/model/factory/storage/StorageEntity'
import { StorageService } from '@/model/factory/storage/StorageService'
import { StructureEntity } from '@/model/factory/structure/StructureEntity'
import { StructureService } from '@/model/factory/structure/StructureService'
import { InventoryEntity } from '@/model/wms/inventory/InventoryEntity'
import { InventoryService } from '@/model/wms/inventory/InventoryService'
import { InventoryTypeEnum } from '@/model/wms/inventory/InventoryTypeEnum'

const request = ref(new QueryRequestPage(InventoryEntity))
const response = ref<QueryResponsePage<InventoryEntity>>(new QueryResponsePage())

const isLoadingTree = ref(false)

const inventoryType = ref(InventoryTypeEnum.STORAGE.key)

const treeData = ref<Array<ITree & RootEntity>>([])

// 新 API Hook — 替换原有的 InventoryService.create(isLoading).getPage()
// 响应式驱动：inventoryQuery 变化时自动重新请求
const inventoryQuery = computed(() => {
  const filter = request.value.filter
  return {
    page: request.value.page.pageNum || 1,
    pageSize: request.value.page.pageSize || 20,
    filter: {
      type: inventoryType.value,
      ...(filter?.storage?.id ? { storage: { id: filter.storage.id } } : {}),
      ...(filter?.structure?.id ? { structure: { id: filter.structure.id } } : {}),
    },
  }
})

const { data: inventoryData, isLoading } = useInventoryList(inventoryQuery)

// 将新 API 响应同步到 airpower QueryResponsePage 格式，保持 ATable 兼容
watch(inventoryData, (data) => {
  if (data) {
    response.value.list = data.list
    response.value.total = data.total
  }
})

async function getStorage() {
  treeData.value = await StorageService.create(isLoadingTree).getList(new QueryRequest(StorageEntity))
}

async function getStructure() {
  treeData.value = await StructureService.create(isLoadingTree).getList(new QueryRequest(StructureEntity))
}

async function onPageChanged(page: QueryPage) {
  request.value.page = page
  // inventoryQuery 已通过 computed 响应式驱动，无需手动调用 getList()
}

const treePlaceHolder = ref('搜索...')

async function inventoryTypeChanged() {
  switch (inventoryType.value) {
    case InventoryTypeEnum.STORAGE.key:
      treePlaceHolder.value = '搜索仓库...'
      getStorage()
      break
    case InventoryTypeEnum.STRUCTURE.key:
      treePlaceHolder.value = '搜索生产单元...'
      getStructure()
      break
    default:
  }

  treeChanged(undefined)
}

async function treeChanged(current: ITree | undefined) {
  if (!current) {
    request.value.filter.exclude('storage', 'structure')
    // inventoryQuery 响应式驱动，自动重新请求
    return
  }
  switch (inventoryType.value) {
    case InventoryTypeEnum.STORAGE.key:
      request.value.filter.storage = (current as StorageEntity).copy()
      request.value.filter.storage.expose('id')
      break
    case InventoryTypeEnum.STRUCTURE.key:
      request.value.filter.structure = (current as StructureEntity).copy()
      request.value.filter.structure.expose('id')
      break
    default:
  }
  // inventoryQuery 响应式驱动，自动重新请求
}

const tableField = computed(() => {
  const list = getTableConfigList(InventoryEntity)
  switch (inventoryType.value) {
    case InventoryTypeEnum.STORAGE.key:
      return list.filter(item => item.key !== 'structureName')
    case InventoryTypeEnum.STRUCTURE.key:
      return list.filter(item => item.key !== 'storageName')
    default:
  }
  return list
})

inventoryTypeChanged()
</script>

<template>
  <ATreeBox
    v-loading="isLoadingTree"
    :placeholder="treePlaceHolder"
    :tree-data="treeData"
    searchable
    @change="treeChanged"
  >
    <APanel>
      <ATable
        v-loading="isLoading"
        :column-list="tableField"
        :data-list="response.list"
        :entity="InventoryEntity"
        :service="InventoryService"
        ctrl-width="60"
        hide-add
        hide-delete
        hide-edit
      >
        <template #afterButton>
          <el-radio-group
            v-model="inventoryType"
            @change="inventoryTypeChanged"
          >
            <el-radio-button
              v-for="view in InventoryTypeEnum.toArray()"
              :key="view.key"
              :label="view.key"
              :value="view.key"
            >
              {{ view.label }}
            </el-radio-button>
          </el-radio-group>
        </template>
        <template #beforePage>
          <APage
            :response="response"
            @change="onPageChanged"
          />
        </template>
      </ATable>
    </APanel>
  </ATreeBox>
</template>

<style lang="scss" scoped></style>
