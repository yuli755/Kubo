<script lang="ts" setup>
import { APanel, APayload, ATable } from '@airpower/web'
import { useMyTable } from '@/hook/useMyTable'
import { PurchasePriceService } from '@/model/channel/purchasePrice/PurchasePriceService'
import { SupplierDetail } from '../supplier/component'
import { Permissions } from '@/config/permissions'
import { PurchasePriceEditor } from './component'

const hook = useMyTable(PurchasePriceService, {
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
