<script lang="ts" setup>
import { ASelector, DialogProps } from '@airpower/web'
import { CustomerService } from '@/hook/channel/useCustomer'
import { CustomerEntity } from '@/model/channel/customer/CustomerEntity'
import { CustomerEditor } from '.'

const props = defineProps(DialogProps.withSelector<CustomerEntity>())
</script>

<template>
  <ASelector
    :editor="CustomerEditor"
    :entity="CustomerEntity"
    :props="props"
    :service="CustomerService"
  />
</template>

<style lang="scss" scoped></style>
