<script lang="ts" setup>
import { APanel, ATable, useTableTree } from '@airpower/web'
import { watch } from 'vue'
import { useStorageTree } from '@/hook/factory'
import { StorageService } from '@/model/factory/storage/StorageService'
import { StorageEditor } from './component'

const hook = useTableTree(StorageService, {
  editView: StorageEditor,
  beforeAddRow(param, row) {
    param.parent = row
    return param
  },
})

// 使用 @tanstack/vue-query 替代 airpower service.getList
const { isFetching, refetch } = useStorageTree()

hook.service.getList = async () => {
  const result = await refetch()
  return result.data ?? []
}

// 同步加载状态到 hook，保持 ATable v-loading 正常工作
watch(isFetching, (v) => {
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
