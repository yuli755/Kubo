<script lang="ts" setup>
import { ASelector, DialogProps } from '@airpower/web'
import { BomService } from '@/hook/mes/useBom'
import { BomEntity } from '@/model/mes/bom/BomEntity'
import { BomEditor } from '.'

const props = defineProps(DialogProps.withSelector<BomEntity>())
</script>

<template>
  <ASelector
    :editor="BomEditor"
    :entity="BomEntity"
    :props="props"
    :service="BomService"
  />
</template>

<style lang="scss" scoped></style>
