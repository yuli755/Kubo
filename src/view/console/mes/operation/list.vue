<script lang="ts" setup>
import { APanel, ATable } from '@airpower/web'
import { useMyTable } from '@/hook/useMyTable'
import { OperationService } from '@/model/mes/operation/OperationService'
import { Permissions } from '@/config/permissions'
import { OperationEditor } from './component'

const hook = useMyTable(OperationService, {
  editView: OperationEditor,
})
</script>

<template>
  <APanel>
    <ATable
      :use-hook="hook"
      :add-permission="Permissions.Mes.Operation.add"
      :delete-permission="Permissions.Mes.Operation.delete"
      :edit-permission="Permissions.Mes.Operation.update"
    />
  </APanel>
</template>

<style lang="scss" scoped></style>
