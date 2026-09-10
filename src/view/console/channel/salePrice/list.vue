<script lang="ts" setup>
import { AMoney, APanel, ATable } from '@airpower/web'
import { onMounted } from 'vue'
import { useMaskPolicy } from '@/composables/useMaskPolicy'
import { Permissions } from '@/config/permissions'
import { SalePriceService } from '@/hook/channel/useSalePrice'
import { useTanStackTable } from '@/hook/useTanStackTable'
import { SalePriceEditor } from './component'

const hook = useTanStackTable(SalePriceService, {
  queryKey: ['channel:salePrice'],
  editView: SalePriceEditor,
})

// 销售单价 price 被后端 @SensitiveField 标记：列表列按 my-mask 策略遮蔽，
// 策略未到达前先遮，拿到明文授权才走 AMoney。
const pricePolicy = useMaskPolicy('channel.salePrice', ['price'])
onMounted(() => pricePolicy.load())
</script>

<template>
  <APanel>
    <ATable
      :use-hook="hook"
      :add-permission="Permissions.Channel.SalePrice.add"
      :delete-permission="Permissions.Channel.SalePrice.delete"
      :edit-permission="Permissions.Channel.SalePrice.update"
    >
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
