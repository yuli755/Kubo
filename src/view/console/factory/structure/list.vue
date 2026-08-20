<script lang="ts" setup>
import { APanel, ATable, useTableTree } from '@airpower/web'
import { StructureService } from '@/model/factory/structure/StructureService'
import { StructureEditor } from './component'

const hook = useTableTree(StructureService, {
  editView: StructureEditor,
  beforeAddRow(param, row) {
    param.parent = row
    return param
  },
})
</script>

<template>
  <APanel>
    <ATable
      :disable-delete="(row) => row.children.length > 0"
      :use-hook="hook"
      add-permission="Structure_add"
      add-row-permission="Structure_add"
      delete-permission="Structure_delete"
      edit-permission="Structure_update"
      ctrl-width="130"
      is-tree
      show-add-row
    />
  </APanel>
</template>

<style lang="scss" scoped></style>
