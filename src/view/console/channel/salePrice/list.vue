<script lang="ts" setup>
import { APanel, ATable } from '@airpower/web'
import { Permissions } from '@/config/permissions'
import { SalePriceService } from '@/hook/channel/useSalePrice'
import { useTanStackTable } from '@/hook/useTanStackTable'
import { SalePriceEditor } from './component'

const hook = useTanStackTable(SalePriceService, {
  queryKey: ['channel:salePrice'],
  editView: SalePriceEditor,
})
</script>

<template>
  <APanel>
    <ATable
      :use-hook="hook"
      :add-permission="Permissions.Channel.SalePrice.add"
      :delete-permission="Permissions.Channel.SalePrice.delete"
      :edit-permission="Permissions.Channel.SalePrice.update"
    />
  </APanel>
</template>

<style lang="scss" scoped></style>
