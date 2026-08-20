<script lang="ts" setup>
import { APanel, ATable } from '@airpower/web'
import { useMyTable } from '@/hook/useMyTable'
import { UnitService } from '@/model/system/unit/UnitService'
import { Permissions } from '@/config/permissions'
import { UnitEditor } from './component'

const hook = useMyTable(UnitService, {
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
