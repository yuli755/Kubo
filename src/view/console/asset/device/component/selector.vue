<script lang="ts" setup>
import { ASelector, DialogProps } from '@airpower/web'
import { DeviceService } from '@/hook/asset/useDevice'
import { DeviceEntity } from '@/model/asset/device/DeviceEntity'
import { DeviceEditor } from '.'

const props = defineProps(DialogProps.withSelector<DeviceEntity>())
</script>

<template>
  <ASelector
    :editor="DeviceEditor"
    :entity="DeviceEntity"
    :props="props"
    :service="DeviceService"
  />
</template>

<style lang="scss" scoped></style>
