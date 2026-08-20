<script lang="ts" setup>
import { APanel, ATable } from '@airpower/web'
import { useTanStackTable } from '@/hook/useTanStackTable'
import { SupplierService } from '@/model/channel/supplier/SupplierService'
import { Permissions } from '@/config/permissions'
import { SupplierEditor } from './component'

const hook = useTanStackTable(SupplierService, {
  queryKey: ['channel:supplier'],
  editView: SupplierEditor,
})
</script>

<template>
  <APanel>
    <ATable
      :use-hook="hook"
      :add-permission="Permissions.Channel.Supplier.add"
      :delete-permission="Permissions.Channel.Supplier.delete"
      :edit-permission="Permissions.Channel.Supplier.update"
    />
  </APanel>
</template>

<style lang="scss" scoped></style>
