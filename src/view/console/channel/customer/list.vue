<script lang="ts" setup>
import { APanel, ATable } from '@airpower/web'
import { useTanStackTable } from '@/hook/useTanStackTable'
import { CustomerService } from '@/model/channel/customer/CustomerService'
import { Permissions } from '@/config/permissions'
import { CustomerEditor } from './component'

const hook = useTanStackTable(CustomerService, {
  queryKey: ['channel:customer'],
  editView: CustomerEditor,
})
</script>

<template>
  <APanel>
    <ATable
      :use-hook="hook"
      :add-permission="Permissions.Channel.Customer.add"
      :delete-permission="Permissions.Channel.Customer.delete"
      :edit-permission="Permissions.Channel.Customer.update"
    />
  </APanel>
</template>

<style lang="scss" scoped></style>
