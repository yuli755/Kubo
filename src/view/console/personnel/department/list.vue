<script lang="ts" setup>
import { APanel, ATable, useTableTree } from '@airpower/web'
import { Permissions } from '@/config/permissions'
import { DepartmentService } from '@/hook/personnel/useDepartment'
import { DepartmentEditor } from './component'

const hook = useTableTree(DepartmentService, {
  editView: DepartmentEditor,
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
      :add-permission="Permissions.Personnel.Department.add"
      :add-row-permission="Permissions.Personnel.Department.addRow"
      :delete-permission="Permissions.Personnel.Department.delete"
      :edit-permission="Permissions.Personnel.Department.update"
      ctrl-width="130"
      is-tree
      show-add-row
    />
  </APanel>
</template>

<style lang="scss" scoped></style>
