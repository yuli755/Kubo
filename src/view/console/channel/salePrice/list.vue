<script lang="ts" setup>
import { APanel, ATable } from '@airpower/web'
import { useMyTable } from '@/hook/useMyTable'
import { SalePriceService } from '@/model/channel/salePrice/SalePriceService'
import { Permissions } from '@/config/permissions'
import { SalePriceEditor } from './component'

const hook = useMyTable(SalePriceService, {
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
