<script lang="ts" setup>
import { APanel, ATable } from '@airpower/web'
import { useMyTable } from '@/hook/useMyTable'
import { ParameterService } from '@/model/iot/parameter/ParameterService'
import { Permissions } from '@/config/permissions'
import { ParameterEditor } from './component'

const hook = useMyTable(ParameterService, {
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
