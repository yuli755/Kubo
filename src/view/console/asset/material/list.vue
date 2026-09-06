<script lang="ts" setup>
import { APanel, ATable } from '@airpower/web'
import { Permissions } from '@/config/permissions'
import { useTanStackTable } from '@/hook/useTanStackTable'
import { MaterialService } from '@/model/asset/material/MaterialService'
import { UnitEntity } from '@/model/system/unit/UnitEntity'
import { MaterialEditor } from './component'

const hook = useTanStackTable(MaterialService, {
  queryKey: ['asset:material'],
  editView: MaterialEditor,
  beforeSearch: (requestData) => {
    if (requestData.filter.unitName) {
      const unit = new UnitEntity()
      unit.name = requestData.filter.unitName
      requestData.filter.unit = unit
      requestData.filter.unitName = undefined
    }
    return requestData
  },
})
</script>

<template>
  <APanel>
    <ATable
      :use-hook="hook"
      :add-permission="Permissions.Asset.Material.add"
      :delete-permission="Permissions.Asset.Material.delete"
      :edit-permission="Permissions.Asset.Material.update"
      :export-permission="Permissions.Asset.Material.export"
      show-export
    />
  </APanel>
</template>

<style lang="scss" scoped></style>
