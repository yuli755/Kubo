<script lang="ts" setup>
import { APanel, ATable, useTableTree } from '@airpower/web'
import { Permissions } from '@/config/permissions'
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
      :add-permission="Permissions.Factory.Structure.add"
      :add-row-permission="Permissions.Factory.Structure.addRow"
      :delete-permission="Permissions.Factory.Structure.delete"
      :edit-permission="Permissions.Factory.Structure.update"
      ctrl-width="130"
      is-tree
      show-add-row
    />
  </APanel>
</template>

<style lang="scss" scoped></style>
