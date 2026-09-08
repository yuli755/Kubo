<script lang="ts" setup>
import { APanel, APayload, ATable } from '@airpower/web'
import { Permissions } from '@/config/permissions'
import { PurchasePriceService } from '@/hook/channel/usePurchasePrice'
import { useTanStackTable } from '@/hook/useTanStackTable'
import { SupplierDetail } from '../supplier/component'
import { PurchasePriceEditor } from './component'

const hook = useTanStackTable(PurchasePriceService, {
  queryKey: ['channel:purchasePrice'],
  editView: PurchasePriceEditor,
})
</script>

<template>
  <APanel>
    <ATable
      :use-hook="hook"
      :add-permission="Permissions.Channel.PurchasePrice.add"
      :delete-permission="Permissions.Channel.PurchasePrice.delete"
      :edit-permission="Permissions.Channel.PurchasePrice.update"
    >
      <template #supplier="{ data }">
        <APayload
          :payload="data.supplier"
          :view="SupplierDetail"
        />
      </template>
    </ATable>
  </APanel>
</template>

<style lang="scss" scoped></style>
