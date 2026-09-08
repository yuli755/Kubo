<script lang="ts" setup>
import { APanel, ATable } from '@airpower/web'
import { Permissions } from '@/config/permissions'
import { ParameterService } from '@/hook/iot/useParameter'
import { useTanStackTable } from '@/hook/useTanStackTable'
import { ParameterEditor } from './component'

const hook = useTanStackTable(ParameterService, {
  queryKey: ['iot:parameter'],
  editView: ParameterEditor,
})
</script>

<template>
  <APanel>
    <ATable
      :disable-delete="(row) => row.isSystem"
      :disable-edit="(row) => row.isSystem"
      :use-hook="hook"
      :add-permission="Permissions.IoT.Parameter.add"
      :delete-permission="Permissions.IoT.Parameter.delete"
      :edit-permission="Permissions.IoT.Parameter.update"
    />
  </APanel>
</template>

<style lang="scss" scoped></style>
