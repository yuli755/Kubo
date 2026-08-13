<script lang="ts" setup>
import { APanel, ATable, useTableTree } from '@airpower/web'
import { watch } from 'vue'
import { useStructureTree } from '@/hook/factory'
import { StructureService } from '@/model/factory/structure/StructureService'
import { StructureEditor } from './component'

const hook = useTableTree(StructureService, {
  editView: StructureEditor,
  beforeAddRow(param, row) {
    param.parent = row
    return param
  },
})

// 使用 @tanstack/vue-query 替代 airpower service.getList
// 树形实体走 unPaginate 路径，onGetList 将结果赋给 hook.list.value
const { data, isLoading } = useStructureTree()

// 将新 API 响应同步到 airpower list，保持 ATable 树渲染兼容
watch(data, (value) => {
  hook.list.value = value ?? []
})

// 同步加载状态到 hook，保持 ATable v-loading 正常工作
watch(isLoading, (v) => {
  hook.isLoading.value = v
})
</script>

<template>
  <APanel>
    <ATable
      :disable-delete="(row) => row.children.length > 0"
      :use-hook="hook"
      ctrl-width="130"
      is-tree
      show-add-row
    />
  </APanel>
</template>

<style lang="scss" scoped></style>
