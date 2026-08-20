<script lang="ts" setup>
import type { PermissionEntity } from '@/model/system/permission/PermissionEntity'

import { APanel, ATable, useTableTree } from '@airpower/web'
import { PermissionService } from '@/model/system/permission/PermissionService'
import { Permissions } from '@/config/permissions'
import { PermissionEditor } from './component'

const hook = useTableTree(PermissionService, {
  editView: PermissionEditor,
  beforeAddRow: (param: PermissionEntity, row: PermissionEntity) => {
    param.parent = row
    return param
  },
})
</script>

<template>
  <APanel>
    <ATable
      :default-expand-all="false"
      :disable-add-row="(row) => row.isSystem"
      :disable-delete="(row) => row.isSystem"
      :disable-edit="(row) => row.isSystem"
      :use-hook="hook"
      :add-permission="Permissions.System.Permission.add"
      :add-row-permission="Permissions.System.Permission.addRow"
      :delete-permission="Permissions.System.Permission.delete"
      :edit-permission="Permissions.System.Permission.update"
      ctrl-width="130"
      is-tree
      show-add-row
    />
  </APanel>
</template>

<style lang="scss" scoped></style>
