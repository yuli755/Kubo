<script lang="ts" setup>
import { ASelector, DialogProps } from '@airpower/web'
import { StorageService } from '@/hook/factory/useStorage'
import { StorageEntity } from '@/model/factory/storage/StorageEntity'

const props = defineProps(DialogProps.withSelector(new StorageEntity()))
</script>

<template>
  <ASelector
    :entity="StorageEntity"
    :props="props"
    :service="StorageService"
    :un-paginate="true"
  />
</template>

<style lang="scss" scoped></style>
