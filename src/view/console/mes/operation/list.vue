<script lang="ts" setup>
import { APanel, ATable } from '@airpower/web'
import { Permissions } from '@/config/permissions'
import { OperationService } from '@/hook/mes/useOperation'
import { useTanStackTable } from '@/hook/useTanStackTable'
import { OperationEditor } from './component'

const hook = useTanStackTable(OperationService, {
  queryKey: ['mes:operation'],
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
