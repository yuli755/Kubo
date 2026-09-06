<script lang="ts" setup>
import { APanel, ATable } from '@airpower/web'
import { Permissions } from '@/config/permissions'
import { useTanStackTable } from '@/hook/useTanStackTable'
import { DataResourceService } from '@/model/system/datapermission/DataResourceService'
import { DataResourceEditor } from './component'

const hook = useTanStackTable(DataResourceService, {
  queryKey: ['system:datapermission:resource'],
  editView: DataResourceEditor,
})
</script>

<template>
  <APanel>
    <ATable
      :use-hook="hook"
      :add-permission="Permissions.System.DataResource.add"
      :delete-permission="Permissions.System.DataResource.delete"
      :edit-permission="Permissions.System.DataResource.update"
    />
  </APanel>
</template>

<style lang="scss" scoped></style>
