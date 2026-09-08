<script lang="ts" setup>
import { ASelector, DialogProps } from '@airpower/web'
import { MaterialService } from '@/hook/asset/useMaterial'
import { MaterialEntity } from '@/model/asset/material/MaterialEntity'
import { ContractEditor } from '.'

const props = defineProps(DialogProps.withSelector<MaterialEntity>())
</script>

<template>
  <ASelector
    :editor="ContractEditor"
    :entity="MaterialEntity"
    :props="props"
    :service="MaterialService"
  />
</template>

<style lang="scss" scoped></style>
