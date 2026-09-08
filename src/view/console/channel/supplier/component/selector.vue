<script lang="ts" setup>
import { ASelector, DialogProps } from '@airpower/web'
import { SupplierService } from '@/hook/channel/useSupplier'
import { SupplierEntity } from '@/model/channel/supplier/SupplierEntity'
import { SupplierEditor } from '.'

const props = defineProps(DialogProps.withSelector<SupplierEntity>())
</script>

<template>
  <ASelector
    :editor="SupplierEditor"
    :entity="SupplierEntity"
    :props="props"
    :service="SupplierService"
  />
</template>

<style lang="scss" scoped></style>
