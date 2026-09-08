<script lang="ts" setup>
import { ASelector, DialogProps } from '@airpower/web'
import { ParameterService } from '@/hook/iot/useParameter'
import { ParameterEntity } from '@/model/iot/parameter/ParameterEntity'
import { ParameterEditor } from '.'

const props = defineProps(DialogProps.withSelector<ParameterEntity>())
</script>

<template>
  <ASelector
    :editor="ParameterEditor"
    :entity="ParameterEntity"
    :props="props"
    :service="ParameterService"
    width="600px"
  />
</template>

<style lang="scss" scoped></style>
