<script lang="ts" setup>
import { APanel, ATable } from '@airpower/web'
import { useMyTable } from '@/hook/useMyTable'
import { SalePriceService } from '@/model/channel/salePrice/SalePriceService'
import { SalePriceEditor } from './component'

const hook = useMyTable(SalePriceService, {
  editView: SalePriceEditor,
})
</script>

<template>
  <APanel>
    <ATable
      :use-hook="hook"
      add-permission="SalePrice_add"
      delete-permission="SalePrice_delete"
      edit-permission="SalePrice_update"
    />
  </APanel>
</template>

<style lang="scss" scoped></style>
