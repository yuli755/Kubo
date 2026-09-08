<script lang="ts" setup>
import { ASelector, DialogProps } from '@airpower/web'
import { InputService } from '@/hook/wms/useInput'
import { InputEntity } from '@/model/wms/input/InputEntity'
import { InputEditor } from '.'

const props = defineProps(DialogProps.withSelector<InputEntity>())
</script>

<template>
  <ASelector
    :editor="InputEditor"
    :entity="InputEntity"
    :props="props"
    :service="InputService"
  />
</template>

<style lang="scss" scoped></style>
