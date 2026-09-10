<script lang="ts" setup>
import { AMoney, APanel, APayload, ATable } from '@airpower/web'
import { onMounted } from 'vue'
import { useMaskPolicy } from '@/composables/useMaskPolicy'
import { Permissions } from '@/config/permissions'
import { PurchasePriceService } from '@/hook/channel/usePurchasePrice'
import { useTanStackTable } from '@/hook/useTanStackTable'
import { SupplierDetail } from '../supplier/component'
import { PurchasePriceEditor } from './component'

const hook = useTanStackTable(PurchasePriceService, {
  queryKey: ['channel:purchasePrice'],
  editView: PurchasePriceEditor,
})

// 采购单价 price 被后端 @SensitiveField 标记：列表列按 my-mask 策略遮蔽，
// 策略未到达前先遮（restricted/masked 都返回保守值），拿到明文授权才走 AMoney。
const pricePolicy = useMaskPolicy('channel.purchasePrice', ['price'])
onMounted(() => pricePolicy.load())
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
      <template #price="{ data }">
        <AMoney
          v-if="!pricePolicy.restricted('price')"
          :money="data.price"
        />
        <span v-else>{{ pricePolicy.masked('price', data.price) }}</span>
      </template>
    </ATable>
  </APanel>
</template>

<style lang="scss" scoped></style>
