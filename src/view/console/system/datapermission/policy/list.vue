<script lang="ts" setup>
import { APanel, ATable } from '@airpower/web'
import { Permissions } from '@/config/permissions'
import { useTanStackTable } from '@/hook/useTanStackTable'
import { DataPolicyService } from '@/model/system/datapermission/DataPolicyService'
import { DataPolicyEditor } from './component'

const hook = useTanStackTable(DataPolicyService, {
  queryKey: ['system:datapermission:policy'],
  editView: DataPolicyEditor,
})
</script>

<template>
  <APanel>
    <ATable
      :use-hook="hook"
      :add-permission="Permissions.System.DataPolicy.add"
      :delete-permission="Permissions.System.DataPolicy.delete"
      :edit-permission="Permissions.System.DataPolicy.update"
    />
  </APanel>
</template>

<style lang="scss" scoped></style>
