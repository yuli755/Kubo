<script lang="ts" setup>
import { APanel, ATable, useTableTree } from '@airpower/web'
import { Permissions } from '@/config/permissions'
import { MenuService } from '@/model/system/menu/MenuService'
import { MenuEditor } from './component'

const hook = useTableTree(MenuService, {
  editView: MenuEditor,
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
      :add-permission="Permissions.System.Menu.add"
      :add-row-permission="Permissions.System.Menu.addRow"
      :delete-permission="Permissions.System.Menu.delete"
      :edit-permission="Permissions.System.Menu.update"
      ctrl-width="130"
      is-tree
      show-add-row
    >
      <template #path="{ data }">
        <el-link :href="data.path">
          {{ data.path }}
        </el-link>
      </template>
    </ATable>
  </APanel>
</template>

<style lang="scss" scoped></style>
