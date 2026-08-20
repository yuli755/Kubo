<script lang="ts" setup>
import { APanel, ATable } from '@airpower/web'
import { useMyTable } from '@/hook/useMyTable'
import { CustomerService } from '@/model/channel/customer/CustomerService'
import { CustomerEditor } from './component'

const hook = useMyTable(CustomerService, {
  editView: CustomerEditor,
})
</script>

<template>
  <APanel>
    <ATable
      :use-hook="hook"
      add-permission="Customer_add"
      delete-permission="Customer_delete"
      edit-permission="Customer_update"
    />
  </APanel>
</template>

<style lang="scss" scoped></style>
