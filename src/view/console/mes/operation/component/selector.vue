<script lang="ts" setup>
import { ASelector, DialogProps } from '@airpower/web'
import { OperationService } from '@/hook/mes/useOperation'
import { OperationEntity } from '@/model/mes/operation/OperationEntity'
import { OperationEditor } from '.'

const props = defineProps(DialogProps.withSelector<OperationEntity>())
</script>

<template>
  <ASelector
    :editor="OperationEditor"
    :entity="OperationEntity"
    :props="props"
    :service="OperationService"
  />
</template>

<style lang="scss" scoped></style>
