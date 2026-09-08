<script lang="ts" setup>
import { ASelector, DialogProps } from '@airpower/web'

import { SaleService } from '@/hook/channel/useSale'
import { SaleEntity } from '@/model/channel/sale/SaleEntity'
import { SaleStatusEnum } from '@/model/channel/sale/SaleStatusEnum'
import { SaleEditor } from '.'

const props = defineProps(DialogProps.withSelector<SaleEntity>())
</script>

<template>
  <ASelector
    :before-search="
      (requestData) => {
        requestData.filter.status = SaleStatusEnum.OUTPUTTING.key
        return requestData
      }
    "
    :editor="SaleEditor"
    :entity="SaleEntity"
    :props="props"
    :service="SaleService"
  />
</template>

<style lang="scss" scoped></style>
