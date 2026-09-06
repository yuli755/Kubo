<script lang="ts" setup>
import { APanel, ATable } from '@airpower/web'
import { Permissions } from '@/config/permissions'
import { useTanStackTable } from '@/hook/useTanStackTable'
import { DataFieldPolicyService } from '@/model/system/datapermission/DataFieldPolicyService'
import { DataFieldPolicyEditor } from './component'

const hook = useTanStackTable(DataFieldPolicyService, {
  queryKey: ['system:datapermission:fieldPolicy'],
  editView: DataFieldPolicyEditor,
})
</script>

<template>
  <APanel>
    <ATable
      :use-hook="hook"
      :add-permission="Permissions.System.DataFieldPolicy.add"
      :delete-permission="Permissions.System.DataFieldPolicy.delete"
      :edit-permission="Permissions.System.DataFieldPolicy.update"
    />
  </APanel>
</template>

<style lang="scss" scoped></style>
