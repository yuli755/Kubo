<script lang="ts" setup>
import { APanel, ATable } from '@airpower/web'
import { Permissions } from '@/config/permissions'
import { UnitService } from '@/hook/system/useUnit'
import { useTanStackTable } from '@/hook/useTanStackTable'
import { UnitEditor } from './component'

const hook = useTanStackTable(UnitService, {
  queryKey: ['system:unit'],
  editView: UnitEditor,
})
</script>

<template>
  <APanel>
    <ATable
      :use-hook="hook"
      :add-permission="Permissions.System.Unit.add"
      :delete-permission="Permissions.System.Unit.delete"
      :edit-permission="Permissions.System.Unit.update"
    />
  </APanel>
</template>

<style lang="scss" scoped></style>
